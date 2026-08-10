package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static use_case.autoschedule.ProblemFixtures.at;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.ImprovementView;
import interface_adapter.viewmodels.PreviewRowView;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import use_case.autoschedule.testdoubles.FakeWeatherContextGateway;

/**
 * Properties every proposed day must have, whatever the search decided.
 *
 * <p>The existing brute-force cross-check proves the search picks the cheapest order. It
 * says nothing about whether the schedule that order becomes is <em>coherent</em> — whether
 * each activity survives exactly once at its own length, whether the journeys between them
 * are real and drawn once each, and whether the figures printed underneath describe that
 * same timeline. Those are separate failures, and the reported Preview had two of them at
 * once while every existing test passed.</p>
 *
 * <p>Scenarios are generated deterministically across the dimensions that actually change
 * the shape of a day: how many activities, whether order is preserved, venues that open late
 * or close early, locks, unavailable periods, and travel that is zero, short, long,
 * asymmetric or departure-sensitive. A failure prints the whole scenario so it can be
 * reproduced by hand.</p>
 */
class ScheduleInvariantsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);
    private static final List<SoftPolicy> ALL_POLICIES = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    /** One generated day, kept whole so a failure can print exactly what produced it. */
    private static final class Scenario {
        private final String label;
        private final Trip trip;
        private final FakeTravelTimeEstimator estimator;
        private final Set<String> locks;
        private final List<TimeWindow> unavailable;
        private final boolean keepOrder;

        private Scenario(String label, Trip trip, FakeTravelTimeEstimator estimator,
                         Set<String> locks, List<TimeWindow> unavailable, boolean keepOrder) {
            this.label = label;
            this.trip = trip;
            this.estimator = estimator;
            this.locks = locks;
            this.unavailable = unavailable;
            this.keepOrder = keepOrder;
        }

        private DayPlanState preview() {
            final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(trip.getId(),
                    trip.getScheduledEvents(), "", false, Collections.emptyList()));
            new AutoScheduleInteractor(new FakeTripRepository(trip), estimator,
                    new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                    ALL_POLICIES, new ScheduleEngine())
                    .preview(new AutoScheduleInputData(trip.getId(), at(9, 0), at(21, 0),
                            TransportationMode.WALKING, locks, unavailable, keepOrder, true));
            return viewModel.getState();
        }

        private String describe(DayPlanState state) {
            final StringBuilder text = new StringBuilder("\n" + label
                    + "\n  keepOrder=" + keepOrder + " locks=" + locks
                    + " unavailable=" + unavailable);
            text.append("\n  before:");
            for (ScheduledEvent event : trip.getScheduledEvents()) {
                text.append("\n    ").append(event.getStartTime()).append('-')
                        .append(event.getEndTime()).append(' ').append(event.getId());
            }
            text.append("\n  proposed:");
            for (PreviewRowView row : state.getPreviewRows()) {
                text.append("\n    ").append(row.getKind()).append(' ').append(row.getStart())
                        .append('-').append(row.getEnd()).append(' ').append(row.getTitle());
            }
            if (state.getMetrics() != null) {
                text.append("\n  reported travel=")
                        .append(state.getMetrics().getTravelAfterMinutes())
                        .append(" waiting=").append(state.getMetrics().getIdleAfterMinutes());
            }
            return text.toString();
        }
    }

    private static Activity place(String id, ActivityCategory category, IndoorOutdoorType kind,
                                  double latitude, double longitude,
                                  LocalTime opening, LocalTime closing) {
        return new Activity(id, "Place " + id, category, new Location(latitude, longitude, id),
                4.5, 60, opening, closing, kind, "none");
    }

    /**
     * Every dimension the audit asks about, walked deterministically rather than sampled, so
     * the same set of days is checked on every run.
     */
    private static List<Scenario> scenarios() {
        final List<Scenario> all = new ArrayList<>();
        final Random random = new Random(7788991L);

        for (int count = 2; count <= 5; count++) {
            for (int variant = 0; variant < 14; variant++) {
                final boolean keepOrder = variant % 2 == 0;
                final boolean lateOpener = variant % 3 == 0;
                final boolean earlyCloser = variant % 5 == 0;
                final boolean asymmetric = variant % 4 < 2;
                final boolean departureSensitive = variant % 7 < 3;
                final int lockCount = variant % 6 == 0 ? 2 : variant % 4 == 0 ? 1 : 0;
                final boolean unavailablePeriod = variant % 3 == 1;

                final List<Activity> places = new ArrayList<>();
                final List<ScheduledEvent> events = new ArrayList<>();
                LocalTime cursor = at(9, 0);
                for (int i = 0; i < count; i++) {
                    final String id = "a" + i;
                    final LocalTime opens = lateOpener && i == count - 1 ? at(13, 0) : at(8, 0);
                    final LocalTime closes = earlyCloser && i == 0 ? at(12, 0) : at(21, 0);
                    final Activity activity = place(id,
                            i % 3 == 0 ? ActivityCategory.FOOD : ActivityCategory.MUSEUM,
                            i % 2 == 0 ? IndoorOutdoorType.OUTDOOR : IndoorOutdoorType.INDOOR,
                            43.6 + i * 0.015, -79.4 + i * 0.015, opens, closes);
                    places.add(activity);
                    LocalTime start = cursor.isBefore(opens) ? opens : cursor;
                    if (start.plusMinutes(60).isAfter(closes)) {
                        start = closes.minusMinutes(60);
                    }
                    events.add(new ScheduledEvent(id, activity, start, start.plusMinutes(60),
                            EventType.ACTIVITY, ""));
                    cursor = start.plusMinutes(90);
                }
                // The entity insists a day runs forwards, so a generated day that folded back
                // on itself is not a scenario at all.
                boolean ordered = true;
                for (int i = 1; i < events.size(); i++) {
                    ordered &= !events.get(i).getStartTime()
                            .isBefore(events.get(i - 1).getEndTime());
                }
                if (!ordered) {
                    continue;
                }

                final Trip trip = new Trip("trip-" + count + "-" + variant, "Toronto", DATE,
                        at(9, 0), at(21, 0), TransportationMode.WALKING);
                trip.replaceSchedule(events);

                final FakeTravelTimeEstimator estimator =
                        new FakeTravelTimeEstimator().timeSensitive(departureSensitive);
                for (int from = 0; from < count; from++) {
                    for (int to = 0; to < count; to++) {
                        if (from == to) {
                            continue;
                        }
                        // Zero, short and long journeys all appear, and asymmetric variants
                        // make the reverse leg cost something different.
                        final int minutes = new int[]{0, 3, 12, 45}[(from + to + variant) % 4];
                        final int reverse = asymmetric ? (minutes + 7) % 50 : minutes;
                        estimator.route("a" + from, "a" + to, minutes);
                        estimator.route("a" + to, "a" + from, reverse);
                    }
                }

                final Set<String> locks = new LinkedHashSet<>();
                for (int i = 0; i < lockCount && i < events.size(); i++) {
                    locks.add(events.get(i).getId());
                }
                final List<TimeWindow> unavailable = unavailablePeriod
                        ? Collections.singletonList(new TimeWindow(at(14, 0), at(15, 0)))
                        : Collections.<TimeWindow>emptyList();

                all.add(new Scenario("count=" + count + " variant=" + variant, trip, estimator,
                        locks, unavailable, keepOrder));
                random.nextInt();
            }
        }
        return all;
    }

    /**
     * The structural contract: a proposed day is the same activities, each once, each its own
     * length, in a sequence a person could actually walk.
     */
    @Test
    void everyProposedDayIsAWalkableSequenceOfTheSameActivities() {
        int checked = 0;
        for (Scenario scenario : scenarios()) {
            final DayPlanState state = scenario.preview();
            final List<PreviewRowView> rows = state.getPreviewRows();
            if (rows.isEmpty()) {
                continue;
            }
            final String detail = scenario.describe(state);

            final Set<String> seen = new HashSet<>();
            final Set<String> travelSeen = new HashSet<>();
            LocalTime previousEnd = null;
            PreviewRowView previousRow = null;

            for (PreviewRowView row : rows) {
                assertTrue(row.getEnd().isAfter(row.getStart()),
                        "no row may end before it starts" + detail);
                if (previousEnd != null) {
                    assertTrue(!row.getStart().isBefore(previousEnd),
                            "rows must not overlap" + detail);
                }
                if (row.getKind() == PreviewRowView.Kind.ACTIVITY) {
                    assertTrue(seen.add(row.getEventId()),
                            "each activity appears exactly once" + detail);
                    assertEquals(60, minutes(row.getStart(), row.getEnd()),
                            "an activity keeps the length it was given" + detail);
                }
                else {
                    assertTrue(travelSeen.add(row.getEventId()),
                            "a journey is drawn exactly once" + detail);
                    assertTrue(previousRow != null
                                    && previousRow.getKind() == PreviewRowView.Kind.ACTIVITY,
                            "a journey always follows the activity it leaves" + detail);
                }
                previousEnd = row.getEnd();
                previousRow = row;
            }

            assertEquals(scenario.trip.getScheduledEvents().size(), seen.size(),
                    "every activity in the day must survive scheduling" + detail);
            checked++;
        }
        assertTrue(checked > 30, "expected many feasible scenarios, got " + checked);
    }

    /**
     * A journey is either drawn or it did not happen. Consecutive activities in different
     * places must be separated by a real, visible leg — never by an unexplained gap, and
     * never by a zero-length row that no one could see.
     */
    @Test
    void consecutiveActivitiesAreJoinedByExactlyOneVisibleJourney() {
        for (Scenario scenario : scenarios()) {
            final DayPlanState state = scenario.preview();
            final List<PreviewRowView> rows = state.getPreviewRows();
            if (rows.isEmpty()) {
                continue;
            }
            final String detail = scenario.describe(state);

            for (int i = 0; i < rows.size(); i++) {
                final PreviewRowView row = rows.get(i);
                if (row.getKind() != PreviewRowView.Kind.TRAVEL) {
                    continue;
                }
                assertTrue(minutes(row.getStart(), row.getEnd()) > 0,
                        "a journey with no duration must not be drawn as a row" + detail);
                assertTrue(i + 1 < rows.size()
                                && rows.get(i + 1).getKind() == PreviewRowView.Kind.ACTIVITY,
                        "a journey must be followed by the activity it reaches" + detail);

                final PreviewRowView destination = rows.get(i + 1);
                assertTrue(!row.getEnd().isAfter(destination.getStart()),
                        "a journey cannot land after the activity it reaches has begun"
                                + detail);
                // Landing exactly on the start is the rule. The only licence to land earlier
                // is a wait the traveller could not have spent travelling anyway: an
                // unavailable period, or a door that is not open yet.
                if (!row.getEnd().equals(destination.getStart())) {
                    final Activity venue = activityFor(scenario, destination.getEventId());
                    final boolean waitingForTheDoors =
                            !venue.getOpeningTime().isBefore(destination.getStart());
                    boolean waitingOutAnAppointment = false;
                    for (TimeWindow window : scenario.unavailable) {
                        waitingOutAnAppointment |= !window.getStart().isBefore(row.getEnd())
                                && !window.getEnd().isBefore(destination.getStart());
                    }
                    assertTrue(waitingForTheDoors || waitingOutAnAppointment,
                            "a journey landed early for no reason a traveller would accept"
                                    + detail);
                }
            }
        }
    }

    /** Hard rules stay hard: opening hours, the traveller's day, and unavailable periods. */
    @Test
    void hardConstraintsHoldInEveryProposedDay() {
        for (Scenario scenario : scenarios()) {
            final DayPlanState state = scenario.preview();
            if (state.getPreviewRows().isEmpty()) {
                continue;
            }
            final String detail = scenario.describe(state);

            for (PreviewRowView row : state.getPreviewRows()) {
                assertTrue(!row.getStart().isBefore(at(9, 0)) && !row.getEnd().isAfter(at(21, 0)),
                        "nothing may fall outside the traveller's day" + detail);
                for (TimeWindow window : scenario.unavailable) {
                    assertTrue(!new TimeWindow(row.getStart(), row.getEnd()).overlaps(window),
                            "nothing may sit inside an unavailable period, travel included"
                                    + detail);
                }
                if (row.getKind() != PreviewRowView.Kind.ACTIVITY) {
                    continue;
                }
                final Activity activity = activityFor(scenario, row.getEventId());
                assertTrue(!row.getStart().isBefore(activity.getOpeningTime())
                                && !row.getEnd().isAfter(activity.getClosingTime()),
                        "an activity must stay inside its opening hours" + detail);
            }

            for (String lockedId : scenario.locks) {
                final PreviewRowView row = activityRow(state, lockedId);
                final ScheduledEvent original = originalEvent(scenario, lockedId);
                if (row != null) {
                    assertEquals(original.getStartTime(), row.getStart(),
                            "a locked activity keeps its time" + detail);
                }
            }
        }
    }

    /** The figures printed under the schedule must be the schedule's own figures. */
    @Test
    void reportedMetricsAgreeWithTheProposedRowsInEveryScenario() {
        for (Scenario scenario : scenarios()) {
            final DayPlanState state = scenario.preview();
            if (state.getPreviewRows().isEmpty() || state.getMetrics() == null) {
                continue;
            }
            final String detail = scenario.describe(state);

            int travel = 0;
            int gaps = 0;
            LocalTime cursor = null;
            for (PreviewRowView row : state.getPreviewRows()) {
                if (row.getKind() == PreviewRowView.Kind.TRAVEL) {
                    travel += minutes(row.getStart(), row.getEnd());
                }
                if (cursor != null && row.getStart().isAfter(cursor)) {
                    gaps += minutes(cursor, row.getStart());
                }
                if (cursor == null || row.getEnd().isAfter(cursor)) {
                    cursor = row.getEnd();
                }
            }
            assertEquals(travel, state.getMetrics().getTravelAfterMinutes(),
                    "reported travel must be the travel drawn" + detail);
            assertEquals(gaps, state.getMetrics().getIdleAfterMinutes(),
                    "reported waiting must be the waiting drawn" + detail);
        }
    }

    /**
     * A card is a claim, so each one has to correspond to something that actually happened.
     * A saving of zero minutes is not an improvement, and an order that changed is not a
     * preserved order.
     */
    @Test
    void everyImprovementCardDescribesSomethingThatActuallyHappened() {
        for (Scenario scenario : scenarios()) {
            final DayPlanState state = scenario.preview();
            if (state.getPreviewRows().isEmpty()) {
                continue;
            }
            final String detail = scenario.describe(state);

            for (ImprovementView card : state.getImprovements()) {
                // The whole card, so a claim is checked wherever its words happen to sit.
                final String headline = card.spoken();
                assertTrue(!headline.startsWith("0 "),
                        "a saving of nothing is not an improvement: " + headline + detail);

                if (headline.contains("waiting removed")) {
                    assertTrue(state.getMetrics().getIdleBeforeMinutes()
                                    > state.getMetrics().getIdleAfterMinutes(),
                            "waiting can only be claimed removed if it fell" + detail);
                }
                if (headline.contains("less travel")) {
                    assertTrue(state.getMetrics().getTravelBeforeMinutes()
                                    > state.getMetrics().getTravelAfterMinutes(),
                            "travel can only be claimed saved if it fell" + detail);
                }
                if (headline.contains("ORDER KEPT")) {
                    final List<String> before = new ArrayList<>();
                    for (ScheduledEvent event : scenario.trip.getScheduledEvents()) {
                        if (event.getEventType() == EventType.ACTIVITY) {
                            before.add(event.getId());
                        }
                    }
                    final List<String> after = new ArrayList<>();
                    for (PreviewRowView row : state.getPreviewRows()) {
                        if (row.getKind() == PreviewRowView.Kind.ACTIVITY) {
                            after.add(row.getEventId());
                        }
                    }
                    assertEquals(before, after,
                            "the order was reported kept but it changed" + detail);
                }
                if (headline.contains("PIN KEPT")) {
                    assertTrue(!scenario.locks.isEmpty(),
                            "nothing was pinned, so nothing can have been kept" + detail);
                }
            }
        }
    }

    /**
     * A routing provider that fails must not be read as "these places are next door to each
     * other". A failed estimate is a warning about the estimate, never a free journey.
     */
    @Test
    void aFailingRoutingProviderNeverBecomesZeroMinutesOfTravel() {
        final Activity first = place("f", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR,
                43.65, -79.38, at(8, 0), at(21, 0));
        final Activity second = place("s", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR,
                43.80, -79.20, at(8, 0), at(21, 0));
        final Trip trip = new Trip("trip-fail", "Toronto", DATE, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(
                new ScheduledEvent("f", first, at(9, 0), at(10, 0), EventType.ACTIVITY, ""),
                new ScheduledEvent("s", second, at(11, 0), at(12, 0), EventType.ACTIVITY, "")));

        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("trip-fail",
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        new AutoScheduleInteractor(new FakeTripRepository(trip),
                new TravelTimeEstimator() {
                    @Override
                    public TravelEstimate estimate(Location from, Location to,
                                                   TransportationMode mode,
                                                   java.time.LocalDateTime departure) {
                        throw new IllegalStateException("routing provider is down");
                    }

                    @Override
                    public boolean isTimeSensitive(TransportationMode mode) {
                        return false;
                    }
                },
                new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                ALL_POLICIES, new ScheduleEngine())
                .preview(new AutoScheduleInputData("trip-fail", at(9, 0), at(21, 0),
                        TransportationMode.WALKING, Collections.emptySet(),
                        Collections.emptyList(), true, true));

        final DayPlanState state = viewModel.getState();
        if (state.getPreviewRows().isEmpty()) {
            // Refusing to propose a day it cannot cost is an acceptable answer; pretending
            // the journey is free is not.
            assertTrue(state.isError() || !state.getMessage().isEmpty(),
                    "a failed provider must say something rather than fail silently");
            return;
        }
        int travel = 0;
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.TRAVEL) {
                travel += minutes(row.getStart(), row.getEnd());
            }
        }
        if (travel == 0) {
            fail("two places twenty kilometres apart were scheduled with no travel at all, "
                    + "which is what a swallowed routing failure looks like: "
                    + state.getPreviewRows());
        }
        assertTrue(!state.getWarnings().isEmpty(),
                "an estimated journey must be flagged as an estimate");
    }

    private static Activity activityFor(Scenario scenario, String eventId) {
        return originalEvent(scenario, eventId).getActivity();
    }

    private static ScheduledEvent originalEvent(Scenario scenario, String eventId) {
        for (ScheduledEvent event : scenario.trip.getScheduledEvents()) {
            if (event.getId().equals(eventId)) {
                return event;
            }
        }
        throw new IllegalStateException("no original event " + eventId);
    }

    private static PreviewRowView activityRow(DayPlanState state, String eventId) {
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.ACTIVITY
                    && row.getEventId().equals(eventId)) {
                return row;
            }
        }
        return null;
    }

    private static int minutes(LocalTime from, LocalTime to) {
        return (to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }
}
