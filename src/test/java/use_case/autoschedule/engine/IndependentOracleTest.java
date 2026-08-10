package use_case.autoschedule.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static use_case.autoschedule.ProblemFixtures.at;
import static use_case.autoschedule.ProblemFixtures.noBlockedWindows;
import static use_case.autoschedule.ProblemFixtures.window;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.OpeningHours;
import use_case.autoschedule.PeriodPlan;
import use_case.autoschedule.PlacedActivity;
import use_case.autoschedule.PolicyContext;
import use_case.autoschedule.ScheduleProblem;
import use_case.autoschedule.ScheduleTask;
import use_case.autoschedule.SchedulingPreferences;
import use_case.autoschedule.TimeWindow;
import use_case.autoschedule.TravelEstimate;
import use_case.autoschedule.TravelMatrix;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;

/**
 * An oracle that finds the best schedule without asking the search how.
 *
 * <p>Every legal visiting order is enumerated and placed by an implementation written straight
 * through in this file — its own opening-hours walk, its own unavailable-window slide, its own
 * just-in-time departures. The production search then has to match it. A mistake made
 * identically on both sides cannot hide, because neither side has seen the other's code.</p>
 *
 * <p>One deliberate exception: the soft policies themselves are used as given. What is under
 * test is whether the <em>search</em> finds the cheapest day under an objective, not whether a
 * second copy of "what counts as a mealtime" agrees with the first. Reimplementing them here
 * would test the copy.</p>
 *
 * <p>This complements {@link BruteForceCrossCheckTest}, which covers plain and bucketed travel;
 * here the dimensions are locks, split opening hours, early closing, and the soft policies
 * individually and together.</p>
 */
class IndependentOracleTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private final ScheduleEngine engine = new ScheduleEngine();

    // --- fixtures ---------------------------------------------------------------------

    private static OpeningHours hours(String... spans) {
        List<OpeningHours.TimeInterval> intervals = new ArrayList<>();
        for (String span : spans) {
            String[] halves = span.split("-");
            intervals.add(new OpeningHours.TimeInterval(
                    LocalTime.parse(halves[0]), LocalTime.parse(halves[1])));
        }
        Map<DayOfWeek, List<OpeningHours.TimeInterval>> week = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            week.put(day, intervals);
        }
        return OpeningHours.of(week);
    }

    private static Activity activity(String id, ActivityCategory category,
                                     IndoorOutdoorType kind, OpeningHours openingHours) {
        return new Activity(id, id, category, new Location(43.65, -79.38, id), 4.5, 60,
                at(8, 0), at(21, 0), kind, "none", "hours", openingHours);
    }

    private static ScheduleTask task(String id, int duration, int index, OpeningHours openingHours,
                                     TimeWindow lockedAt, ActivityCategory category,
                                     IndoorOutdoorType kind) {
        return new ScheduleTask(id, activity(id, category, kind, openingHours), duration, index,
                lockedAt, WEDNESDAY);
    }

    /** One period, so the oracle and the engine cannot disagree about which bucket applies. */
    private static TravelMatrix matrixOf(List<ScheduleTask> tasks, TimeWindow availability,
                                         int[][] minutes) {
        PeriodPlan plan = PeriodPlan.forRun(availability, false,
                Math.max(1, tasks.size() * (tasks.size() - 1)));
        TravelMatrix.Builder builder = TravelMatrix.builder(plan);
        for (int from = 0; from < tasks.size(); from++) {
            for (int to = 0; to < tasks.size(); to++) {
                if (from == to) {
                    continue;
                }
                for (use_case.autoschedule.DeparturePeriod period : plan.activePeriods()) {
                    builder.put(tasks.get(from).getEventId(), tasks.get(to).getEventId(), period,
                            TravelEstimate.routed(minutes[from][to]));
                }
            }
        }
        return builder.build();
    }

    // --- the oracle -------------------------------------------------------------------

    /** Every legal complete schedule, found by enumeration rather than by search. */
    private List<List<PlacedActivity>> allLegalSchedules(ScheduleProblem problem) {
        List<List<ScheduleTask>> orders = new ArrayList<>();
        permute(new ArrayList<>(problem.allTasks()), new ArrayList<>(), orders);

        List<List<PlacedActivity>> legal = new ArrayList<>();
        for (List<ScheduleTask> order : orders) {
            List<PlacedActivity> placed = placeIndependently(problem, order);
            if (placed != null) {
                legal.add(placed);
            }
        }
        return legal;
    }

    private void permute(List<ScheduleTask> remaining, List<ScheduleTask> prefix,
                         List<List<ScheduleTask>> out) {
        if (remaining.isEmpty()) {
            out.add(new ArrayList<>(prefix));
            return;
        }
        for (int i = 0; i < remaining.size(); i++) {
            List<ScheduleTask> rest = new ArrayList<>(remaining);
            ScheduleTask chosen = rest.remove(i);
            prefix.add(chosen);
            permute(rest, prefix, out);
            prefix.remove(prefix.size() - 1);
        }
    }

    /**
     * Places one order, written from the rules rather than by calling the placer.
     *
     * <p>Locked activities must sit exactly where they are pinned; everything else takes the
     * earliest time that clears travel, availability, an opening interval and every unavailable
     * window.</p>
     */
    private List<PlacedActivity> placeIndependently(ScheduleProblem problem,
                                                    List<ScheduleTask> order) {
        List<PlacedActivity> placements = new ArrayList<>();
        LocalTime cursor = problem.getAvailability().getStart();
        ScheduleTask previous = null;

        for (ScheduleTask task : order) {
            int travel = previous == null ? 0 : problem.getTravel()
                    .estimateAt(previous.getEventId(), task.getEventId(), cursor).getMinutes();
            // A journey may not run through an unavailable window either: the traveller waits
            // and sets out once it has passed.
            LocalTime departure = cursor;
            for (int guard = 0; guard < 8 && travel > 0; guard++) {
                LocalTime landing = departure.plusMinutes(travel);
                LocalTime pushed = departure;
                for (TimeWindow blocked : problem.getUnavailableWindows()) {
                    if (blocked.overlaps(new TimeWindow(departure, landing))) {
                        pushed = blocked.getEnd().isAfter(pushed) ? blocked.getEnd() : pushed;
                    }
                }
                if (pushed.equals(departure)) {
                    break;
                }
                departure = pushed;
            }
            LocalTime arrival = departure.plusMinutes(travel);

            LocalTime start;
            if (task.isLocked()) {
                start = task.getLockedAt().getStart();
                if (arrival.isAfter(start) || travel > 0 && blockedWaitEnd(
                        arrival, start, problem.getUnavailableWindows()) != null) {
                    return null;
                }
            } else {
                start = earliestLegalStart(task, problem, arrival);
                if (start == null) {
                    return null;
                }
                // Reaching the destination before a block and waiting through it is not a
                // legal substitute for travelling afterwards. Move the unlocked destination
                // with a fresh journey from the end of the crossed block.
                if (previous != null && travel > 0) {
                    for (int guard = 0;
                         guard <= problem.getUnavailableWindows().size(); guard++) {
                        LocalTime resume = blockedWaitEnd(
                                arrival, start, problem.getUnavailableWindows());
                        if (resume == null) {
                            break;
                        }
                        departure = resume;
                        for (int travelGuard = 0; travelGuard < 8 && travel > 0;
                             travelGuard++) {
                            travel = problem.getTravel().estimateAt(previous.getEventId(),
                                    task.getEventId(), departure).getMinutes();
                            LocalTime landing = departure.plusMinutes(travel);
                            LocalTime pushed = departure;
                            for (TimeWindow blocked : problem.getUnavailableWindows()) {
                                if (blocked.overlaps(new TimeWindow(departure, landing))) {
                                    pushed = blocked.getEnd().isAfter(pushed)
                                            ? blocked.getEnd() : pushed;
                                }
                            }
                            if (pushed.equals(departure)) {
                                break;
                            }
                            departure = pushed;
                        }
                        arrival = departure.plusMinutes(travel);
                        start = earliestLegalStart(task, problem, arrival);
                        if (start == null) {
                            return null;
                        }
                    }
                }
            }
            LocalTime end = start.plusMinutes(task.getDurationMinutes());
            if (end.isAfter(problem.getAvailability().getEnd())
                    || start.isBefore(problem.getAvailability().getStart())
                    || !insideOneOpeningInterval(task, start, end)
                    || overlapsAnyUnavailable(problem, start, end)) {
                return null;
            }

            int idle = minutesBetween(cursor, start) - travel;
            LocalTime setOutAt = departure;
            int avoidable = avoidableIdle(task, arrival, start, problem);
            placements.add(new PlacedActivity(task, start, end,
                    previous == null ? null : setOutAt, travel, Math.max(0, idle), avoidable));
            cursor = end;
            previous = task;
        }
        return placements;
    }

    private LocalTime blockedWaitEnd(LocalTime arrival, LocalTime start,
                                     List<TimeWindow> blocked) {
        if (!start.isAfter(arrival)) {
            return null;
        }
        LocalTime resume = null;
        TimeWindow wait = new TimeWindow(arrival, start);
        for (TimeWindow window : blocked) {
            if (window.overlaps(wait) && (resume == null || window.getEnd().isAfter(resume))) {
                resume = window.getEnd();
            }
        }
        return resume;
    }

    private LocalTime earliestLegalStart(ScheduleTask task, ScheduleProblem problem,
                                         LocalTime arrival) {
        LocalTime candidate = arrival.isBefore(problem.getAvailability().getStart())
                ? problem.getAvailability().getStart() : arrival;
        for (int guard = 0; guard < 16; guard++) {
            LocalTime intoHours = intoAnOpeningInterval(task, candidate);
            if (intoHours == null) {
                return null;
            }
            LocalTime end = intoHours.plusMinutes(task.getDurationMinutes());
            LocalTime pushed = intoHours;
            for (TimeWindow blocked : problem.getUnavailableWindows()) {
                if (blocked.overlaps(new TimeWindow(intoHours, end))) {
                    pushed = blocked.getEnd().isAfter(pushed) ? blocked.getEnd() : pushed;
                }
            }
            if (pushed.equals(intoHours)) {
                return intoHours;
            }
            candidate = pushed;
        }
        return null;
    }

    private LocalTime intoAnOpeningInterval(ScheduleTask task, LocalTime earliest) {
        LocalTime best = null;
        for (TimeWindow open : task.getOpeningWindows()) {
            LocalTime candidate = earliest.isBefore(open.getStart()) ? open.getStart() : earliest;
            if (!candidate.plusMinutes(task.getDurationMinutes()).isAfter(open.getEnd())
                    && (best == null || candidate.isBefore(best))) {
                best = candidate;
            }
        }
        return best;
    }

    private boolean insideOneOpeningInterval(ScheduleTask task, LocalTime start, LocalTime end) {
        for (TimeWindow open : task.getOpeningWindows()) {
            if (!start.isBefore(open.getStart()) && !end.isAfter(open.getEnd())) {
                return true;
            }
        }
        return false;
    }

    private boolean overlapsAnyUnavailable(ScheduleProblem problem, LocalTime start,
                                           LocalTime end) {
        for (TimeWindow blocked : problem.getUnavailableWindows()) {
            if (blocked.overlaps(new TimeWindow(start, end))) {
                return true;
            }
        }
        return false;
    }

    private int avoidableIdle(ScheduleTask task, LocalTime arrival, LocalTime start,
                              ScheduleProblem problem) {
        LocalTime opens = task.getOpeningWindows().isEmpty()
                ? task.getOpeningTime() : task.openingWindowFor(start,
                        start.plusMinutes(task.getDurationMinutes())) == null
                        ? task.getOpeningTime()
                        : task.openingWindowFor(start,
                                start.plusMinutes(task.getDurationMinutes())).getStart();
        LocalTime from = arrival.isBefore(opens) ? opens : arrival;
        if (!start.isAfter(from)) {
            return 0;
        }
        int total = minutesBetween(from, start);
        int blocked = 0;
        for (TimeWindow window : problem.getUnavailableWindows()) {
            LocalTime overlapStart = window.getStart().isAfter(from) ? window.getStart() : from;
            LocalTime overlapEnd = window.getEnd().isBefore(start) ? window.getEnd() : start;
            if (overlapEnd.isAfter(overlapStart)) {
                blocked += minutesBetween(overlapStart, overlapEnd);
            }
        }
        return Math.max(0, total - blocked);
    }

    /** The documented objective, added up here rather than asked of the engine. */
    private int costOf(List<PlacedActivity> placements, SchedulingPreferences preferences) {
        int travel = 0;
        int idle = 0;
        int penalty = 0;
        int displacement = 0;
        List<PlacedActivity> ordered = new ArrayList<>(placements);
        Collections.sort(ordered, (left, right) -> left.getStart().compareTo(right.getStart()));
        for (int position = 0; position < ordered.size(); position++) {
            PlacedActivity placed = ordered.get(position);
            travel += placed.getTravelMinutesBefore();
            idle += placed.getAvoidableIdleMinutes();
            for (SoftPolicy policy : preferences.getPolicies()) {
                penalty += policy.penaltyMinutes(placed, preferences.getContext());
            }
            displacement += Math.abs(position - placed.getTask().getOriginalIndex());
        }
        if (!preferences.countsTravel()) {
            travel = 0;
        }
        if (!preferences.countsIdle()) {
            idle = 0;
        }
        return travel + idle + penalty + preferences.orderPenaltyFor(displacement);
    }

    private static int minutesBetween(LocalTime from, LocalTime to) {
        return (to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }

    /** Asserts the search found a schedule costing exactly what the best legal one costs. */
    private void assertMatchesOracle(ScheduleProblem problem, String scenario) {
        List<List<PlacedActivity>> legal = allLegalSchedules(problem);
        ScheduleSearchResult found = engine.search(problem, SearchBudget.defaultBudget());

        if (legal.isEmpty()) {
            assertTrue(!found.isFound(),
                    "the search produced a schedule the oracle says is illegal: " + scenario
                            + describe(found));
            return;
        }
        int best = Integer.MAX_VALUE;
        List<PlacedActivity> bestPlan = null;
        for (List<PlacedActivity> candidate : legal) {
            int cost = costOf(candidate, problem.getPreferences());
            if (cost < best) {
                best = cost;
                bestPlan = candidate;
            }
        }
        assertTrue(found.isFound(),
                "the oracle found " + legal.size() + " legal schedules and the search found "
                        + "none: " + scenario);
        assertEquals(best, found.getPlan().getScore().practicalCostMinutes(),
                "the search did not find the cheapest legal day: " + scenario
                        + "\noracle:" + describe(bestPlan) + "\nengine:" + describe(found));
    }

    private static String describe(List<PlacedActivity> placements) {
        StringBuilder text = new StringBuilder("\n");
        if (placements == null) {
            return text.append("    no schedule").toString();
        }
        for (PlacedActivity placed : placements) {
            text.append("    ").append(placed.getTask().getEventId()).append(' ')
                    .append(placed.getStart()).append('-').append(placed.getEnd())
                    .append("  travel ").append(placed.getTravelMinutesBefore())
                    .append(" departing ").append(placed.getTravelDeparture())
                    .append("  avoidable ").append(placed.getAvoidableIdleMinutes())
                    .append("  hours ").append(placed.getTask().getOpeningWindows())
                    .append(placed.getTask().isLocked() ? "  LOCKED" : "").append('\n');
        }
        return text.toString();
    }

    /** The schedule the search returned, for reading when the oracle disagrees. */
    private static String describe(ScheduleSearchResult result) {
        if (!result.isFound()) {
            return " (no schedule)";
        }
        StringBuilder text = new StringBuilder("\n");
        for (PlacedActivity placed : result.getPlan().getPlacements()) {
            text.append("    ").append(placed.getTask().getEventId()).append(' ')
                    .append(placed.getStart()).append('-').append(placed.getEnd())
                    .append("  travel ").append(placed.getTravelMinutesBefore())
                    .append(" departing ").append(placed.getTravelDeparture())
                    .append("  hours ").append(placed.getTask().getOpeningWindows())
                    .append(placed.getTask().isLocked() ? "  LOCKED" : "").append('\n');
        }
        return text.toString();
    }

    private static SchedulingPreferences with(SoftPolicy... policies) {
        return SchedulingPreferences.builtIn(Arrays.asList(policies), false,
                PolicyContext.empty());
    }

    // --- scenarios --------------------------------------------------------------------

    @Test
    void oneLockedActivityAmongThree() {
        TimeWindow availability = window(9, 21);
        List<ScheduleTask> tasks = Arrays.asList(
                task("a", 60, 0, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR),
                task("pinned", 60, 1, hours("08:00-20:00"),
                        new TimeWindow(at(13, 0), at(14, 0)),
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR),
                task("c", 60, 2, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR));
        int[][] travel = {{0, 20, 35}, {20, 0, 15}, {35, 15, 0}};

        assertMatchesOracle(new ScheduleProblem(availability, tasks, noBlockedWindows(),
                matrixOf(tasks, availability, travel)), "one lock among three");
    }

    @Test
    void twoLockedActivitiesLeaveOnlyOneThingToDecide() {
        TimeWindow availability = window(9, 21);
        List<ScheduleTask> tasks = Arrays.asList(
                task("free", 60, 0, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR),
                task("pin1", 60, 1, hours("08:00-20:00"),
                        new TimeWindow(at(11, 0), at(12, 0)),
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR),
                task("pin2", 60, 2, hours("08:00-20:00"),
                        new TimeWindow(at(16, 0), at(17, 0)),
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR));
        int[][] travel = {{0, 10, 25}, {10, 0, 30}, {25, 30, 0}};

        assertMatchesOracle(new ScheduleProblem(availability, tasks, noBlockedWindows(),
                matrixOf(tasks, availability, travel)), "two locks");
    }

    @Test
    void splitOpeningHoursCannotBeStraddled() {
        TimeWindow availability = window(9, 21);
        List<ScheduleTask> tasks = Arrays.asList(
                task("split", 90, 0, hours("11:00-14:30", "18:00-22:00"), null,
                        ActivityCategory.FOOD, IndoorOutdoorType.INDOOR),
                task("open", 60, 1, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR),
                task("other", 60, 2, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR));
        int[][] travel = {{0, 12, 30}, {12, 0, 18}, {30, 18, 0}};

        assertMatchesOracle(new ScheduleProblem(availability, tasks, noBlockedWindows(),
                matrixOf(tasks, availability, travel)), "split hours");
    }

    @Test
    void anEarlyClosingVenueForcesTheOrder() {
        TimeWindow availability = window(9, 21);
        List<ScheduleTask> tasks = Arrays.asList(
                task("closesEarly", 60, 0, hours("08:00-11:30"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR),
                task("late", 60, 1, hours("14:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR),
                task("any", 60, 2, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR));
        int[][] travel = {{0, 25, 15}, {25, 0, 20}, {15, 20, 0}};

        assertMatchesOracle(new ScheduleProblem(availability, tasks, noBlockedWindows(),
                matrixOf(tasks, availability, travel)), "early closing");
    }

    @Test
    void twoUnavailableWindowsInOneDay() {
        TimeWindow availability = window(9, 21);
        List<ScheduleTask> tasks = Arrays.asList(
                task("a", 60, 0, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR),
                task("b", 60, 1, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR),
                task("c", 60, 2, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR));
        List<TimeWindow> blocked = Arrays.asList(
                new TimeWindow(at(10, 0), at(11, 0)), new TimeWindow(at(14, 0), at(16, 0)));
        int[][] travel = {{0, 15, 20}, {15, 0, 10}, {20, 10, 0}};

        assertMatchesOracle(new ScheduleProblem(availability, tasks, blocked,
                matrixOf(tasks, availability, travel)), "two unavailable windows");
    }

    @Test
    void eachSoftPolicyOnItsOwn() {
        SoftPolicy[] policies = {
            new MealWindowPolicy(), new DaylightPolicy(), new WeatherSuitabilityPolicy(),
        };
        for (SoftPolicy policy : policies) {
            TimeWindow availability = window(9, 21);
            List<ScheduleTask> tasks = Arrays.asList(
                    task("meal", 60, 0, hours("08:00-20:00"), null,
                            ActivityCategory.FOOD, IndoorOutdoorType.INDOOR),
                    task("outdoor", 60, 1, hours("08:00-20:00"), null,
                            ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR),
                    task("indoor", 60, 2, hours("08:00-20:00"), null,
                            ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR));
            int[][] travel = {{0, 18, 24}, {18, 0, 12}, {24, 12, 0}};
            assertMatchesOracle(new ScheduleProblem(availability, tasks, noBlockedWindows(),
                            matrixOf(tasks, availability, travel), with(policy)),
                    "policy " + policy.id());
        }
    }

    @Test
    void allThreeSoftPoliciesTogether() {
        TimeWindow availability = window(9, 21);
        List<ScheduleTask> tasks = Arrays.asList(
                task("meal", 60, 0, hours("08:00-20:00"), null,
                        ActivityCategory.FOOD, IndoorOutdoorType.INDOOR),
                task("outdoor", 60, 1, hours("08:00-20:00"), null,
                        ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR),
                task("indoor", 60, 2, hours("08:00-20:00"), null,
                        ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR));
        int[][] travel = {{0, 18, 24}, {18, 0, 12}, {24, 12, 0}};

        assertMatchesOracle(new ScheduleProblem(availability, tasks, noBlockedWindows(),
                        matrixOf(tasks, availability, travel),
                        with(new MealWindowPolicy(), new DaylightPolicy(),
                                new WeatherSuitabilityPolicy())),
                "all three policies");
    }

    /**
     * Hundreds of generated days, checked against the oracle. A failure prints the seed and the
     * whole problem, so it can be re-run exactly.
     */
    @Test
    void seededRandomDaysAlwaysMatchTheOracle() {
        int compared = 0;
        for (long seed = 1; seed <= 120; seed++) {
            Random random = new Random(seed);
            int count = 2 + random.nextInt(3);
            List<ScheduleTask> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                boolean splitHours = random.nextInt(4) == 0;
                OpeningHours openingHours = splitHours
                        ? hours("09:00-12:00", "15:00-20:00") : hours("08:00-20:00");
                // Only locks the Interactor would actually forward. ProblemValidator refuses a
                // lock that falls outside its venue's opening windows or inside an unavailable
                // period, so generating one here would compare the engine against a problem it
                // is never given; the refusals themselves are pinned separately below.
                boolean lockable = !splitHours && (10 + i) != 13 && random.nextInt(6) == 0;
                TimeWindow lockedAt = lockable
                        ? new TimeWindow(at(10 + i, 0), at(11 + i, 0)) : null;
                tasks.add(task("t" + i, 60, i, openingHours, lockedAt,
                        i % 2 == 0 ? ActivityCategory.FOOD : ActivityCategory.MUSEUM,
                        i % 3 == 0 ? IndoorOutdoorType.OUTDOOR : IndoorOutdoorType.INDOOR));
            }
            int[][] travel = new int[count][count];
            for (int from = 0; from < count; from++) {
                for (int to = 0; to < count; to++) {
                    // Asymmetric on purpose: a matrix that is its own transpose hides bugs.
                    travel[from][to] = from == to ? 0 : random.nextInt(40);
                }
            }
            TimeWindow availability = window(9, 21);
            List<TimeWindow> blocked = random.nextBoolean()
                    ? Collections.singletonList(new TimeWindow(at(13, 0), at(14, 0)))
                    : noBlockedWindows();
            ScheduleProblem problem = new ScheduleProblem(availability, tasks, blocked,
                    matrixOf(tasks, availability, travel),
                    with(new MealWindowPolicy(), new DaylightPolicy()));

            try {
                assertMatchesOracle(problem, "seed " + seed);
            } catch (AssertionError failure) {
                fail("seed " + seed + " · " + count + " activities · travel "
                        + Arrays.deepToString(travel) + " · blocked " + blocked + "\n"
                        + failure.getMessage());
            }
            compared++;
        }
        assertTrue(compared == 120, "every seed should have been compared");
    }
}
