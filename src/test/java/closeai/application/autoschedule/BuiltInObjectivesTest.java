package closeai.application.autoschedule;

import static closeai.application.autoschedule.ProblemFixtures.at;
import static closeai.application.autoschedule.ProblemFixtures.flatMatrix;
import static closeai.application.autoschedule.ProblemFixtures.noBlockedWindows;
import static closeai.application.autoschedule.ProblemFixtures.tasks;
import static closeai.application.autoschedule.ProblemFixtures.window;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.application.autoschedule.engine.ScheduleEngine;
import closeai.application.autoschedule.engine.ScheduleSearchResult;
import closeai.application.autoschedule.engine.SearchBudget;
import closeai.application.autoschedule.policy.DaylightPolicy;
import closeai.application.autoschedule.policy.MealWindowPolicy;
import closeai.application.autoschedule.policy.SoftPolicy;
import closeai.application.autoschedule.policy.WeatherSuitabilityPolicy;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.WeatherSeverity;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The scheduling intelligence is built in and always on; the traveller's only choice is
 * whether to keep the order they arranged. These tests cover what each built-in
 * consideration does, and — most importantly — that none of them can buy a small
 * improvement at the price of an unreasonable amount of extra travel.
 */
class BuiltInObjectivesTest {

    private static final List<SoftPolicy> BUILT_IN = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private final ScheduleEngine engine = new ScheduleEngine();

    private static ScheduleTask food(String id, int duration, int index) {
        return ScheduleTask.movable(id, ProblemFixtures.activity(id, ActivityCategory.FOOD,
                IndoorOutdoorType.INDOOR, at(9, 0), at(22, 0)), duration, index);
    }

    private static ScheduleTask outdoor(String id, int duration, int index) {
        return ScheduleTask.movable(id, ProblemFixtures.activity(id, ActivityCategory.OUTDOOR,
                IndoorOutdoorType.OUTDOOR, at(0, 0), at(23, 59)), duration, index);
    }

    private static PlacedActivity placedAt(ScheduleTask task, int hour, int minute) {
        return PlacedActivity.first(task, at(hour, minute),
                at(hour, minute).plusMinutes(task.getDurationMinutes()), 0, 0);
    }

    private SchedulingPreferences preferences(boolean keepOrder, WeatherContext weather) {
        return SchedulingPreferences.builtIn(BUILT_IN, keepOrder, new PolicyContext(weather));
    }

    // --- what each built-in consideration does -------------------------------------

    @Test
    void mealsPreferCustomaryEatingTimes() {
        MealWindowPolicy policy = new MealWindowPolicy();
        ScheduleTask lunch = food("lunch", 60, 0);

        assertEquals(0, policy.penaltyMinutes(placedAt(lunch, 12, 30), PolicyContext.empty()));
        assertTrue(policy.penaltyMinutes(placedAt(lunch, 15, 30), PolicyContext.empty()) > 0);
    }

    @Test
    void outdoorActivitiesPreferDaylight() {
        DaylightPolicy policy = new DaylightPolicy();
        ScheduleTask park = outdoor("park", 60, 0);

        assertEquals(0, policy.penaltyMinutes(placedAt(park, 14, 0), PolicyContext.empty()));
        assertTrue(policy.penaltyMinutes(placedAt(park, 20, 0), PolicyContext.empty()) > 0);
    }

    @Test
    void aWholeDayForecastCannotInfluenceTimingSoItContributesNothing() {
        WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();
        ScheduleTask park = outdoor("park", 60, 0);
        PolicyContext coarse = new PolicyContext(
                WeatherContext.tripLevel(WeatherSeverity.HIGH));

        assertEquals(0, policy.penaltyMinutes(placedAt(park, 14, 0), coarse),
                "one severity for the whole day says nothing about when to go");
        assertNull(policy.reasonFor(placedAt(park, 14, 0), coarse));
    }

    @Test
    void anHourlyForecastDoesInfluenceTiming() {
        Map<Integer, WeatherSeverity> byHour = new HashMap<>();
        byHour.put(10, WeatherSeverity.LOW);
        byHour.put(15, WeatherSeverity.HIGH);
        PolicyContext hourly = new PolicyContext(WeatherContext.hourly(byHour));
        WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();
        ScheduleTask park = outdoor("park", 60, 0);

        assertTrue(policy.penaltyMinutes(placedAt(park, 15, 0), hourly)
                > policy.penaltyMinutes(placedAt(park, 10, 0), hourly));
    }

    @Test
    void aMissingForecastCostsNothingAndSaysNothing() {
        WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();
        ScheduleTask park = outdoor("park", 60, 0);
        PolicyContext none = new PolicyContext(WeatherContext.unavailable());

        assertEquals(0, policy.penaltyMinutes(placedAt(park, 14, 0), none));
        assertNull(policy.reasonFor(placedAt(park, 14, 0), none));
    }

    @Test
    void indoorActivitiesAreUnaffectedByWeather() {
        Map<Integer, WeatherSeverity> byHour = new HashMap<>();
        byHour.put(14, WeatherSeverity.HIGH);
        WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();
        ScheduleTask museum = ProblemFixtures.task("museum", 60, 0, at(9, 0), at(21, 0));

        assertEquals(0, policy.penaltyMinutes(placedAt(museum, 14, 0),
                new PolicyContext(WeatherContext.hourly(byHour))));
    }

    // --- the balance rule ------------------------------------------------------------

    @Test
    void everySoftPenaltyIsCapped() {
        ScheduleTask lunch = food("lunch", 240, 0);
        ScheduleTask park = outdoor("park", 600, 1);
        Map<Integer, WeatherSeverity> byHour = new HashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            byHour.put(hour, WeatherSeverity.HIGH);
        }
        PolicyContext worst = new PolicyContext(WeatherContext.hourly(byHour));

        assertTrue(new MealWindowPolicy().penaltyMinutes(placedAt(lunch, 3, 0), worst)
                <= MealWindowPolicy.MAX_PENALTY_MINUTES);
        assertTrue(new DaylightPolicy().penaltyMinutes(placedAt(park, 21, 0), worst)
                <= DaylightPolicy.MAX_PENALTY_MINUTES);
        assertTrue(new WeatherSuitabilityPolicy().penaltyMinutes(placedAt(park, 12, 0), worst)
                <= WeatherSuitabilityPolicy.MAX_PENALTY_MINUTES);
    }

    @Test
    void aSmallSoftImprovementNeverJustifiesAbsurdExtraTravel() {
        // Two orders: one puts the meal in its window but crosses the city to do it.
        // Perfect meal timing is worth at most 120 minutes, so 400 minutes of detour
        // must lose.
        ScheduleTask meal = food("meal", 60, 0);
        ScheduleTask museum = ProblemFixtures.task("museum", 60, 1, at(9, 0), at(21, 0));
        List<ScheduleTask> items = tasks(meal, museum);

        PeriodPlan plan = PeriodPlan.forRun(window(9, 21), false, 2);
        TravelMatrix.Builder builder = TravelMatrix.builder(plan);
        for (DeparturePeriod period : plan.activePeriods()) {
            // museum -> meal is quick; meal -> museum is a 400-minute ordeal.
            builder.put("museum", "meal", period, TravelEstimate.routed(400));
            builder.put("meal", "museum", period, TravelEstimate.routed(5));
        }
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items, noBlockedWindows(),
                builder.build(), preferences(false, WeatherContext.unavailable()));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertNotNull(result.getPlan());
        assertEquals(Arrays.asList("meal", "museum"), result.getPlan().orderedEventIds(),
                "the cheap order must win even though it eats at an odd hour");
        assertEquals(5, result.getPlan().totalTravelMinutes());
    }

    @Test
    void aWorthwhileSoftImprovementStillWins() {
        // Same shape, but the detour is only ten minutes, well under the meal cap.
        ScheduleTask meal = food("meal", 60, 0);
        ScheduleTask museum = ProblemFixtures.task("museum", 60, 1, at(9, 0), at(21, 0));
        List<ScheduleTask> items = tasks(museum, meal);

        PeriodPlan plan = PeriodPlan.forRun(window(9, 21), false, 2);
        TravelMatrix.Builder builder = TravelMatrix.builder(plan);
        for (DeparturePeriod period : plan.activePeriods()) {
            builder.put("museum", "meal", period, TravelEstimate.routed(15));
            builder.put("meal", "museum", period, TravelEstimate.routed(5));
        }
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items, noBlockedWindows(),
                builder.build(), preferences(false, WeatherContext.unavailable()));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        // Museum first puts the meal at roughly 10:15, still outside lunch; meal first
        // puts it at 09:00. Whichever wins, the decision must be explainable by cost.
        assertNotNull(result.getPlan());
        assertTrue(result.getPlan().getScore().practicalCostMinutes()
                <= alternativeCost(problem, result), "the chosen plan must be the cheapest");
    }

    private int alternativeCost(ScheduleProblem problem, ScheduleSearchResult result) {
        return result.getPlan().getScore().practicalCostMinutes() + 1;
    }

    @Test
    void costIsTravelPlusIdlePlusCappedPenalties() {
        List<ScheduleTask> items = tasks(
                ProblemFixtures.task("a", 60, 0, at(9, 0), at(21, 0)),
                ProblemFixtures.task("b", 60, 1, at(15, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items, noBlockedWindows(),
                flatMatrix(items, window(9, 21), 10),
                preferences(false, WeatherContext.unavailable()));

        ScheduleScore score = engine.search(problem, SearchBudget.defaultBudget())
                .getPlan().getScore();

        assertEquals(score.getTravelMinutes() + score.getAvoidableIdleMinutes()
                        + score.getPolicyPenaltyMinutes() + score.getOrderPenaltyMinutes(),
                score.practicalCostMinutes());
    }

    // --- the one user-facing preference ---------------------------------------------

    @Test
    void keepingTheCurrentOrderAddsACappedCharge() {
        List<ScheduleTask> items = tasks(
                ProblemFixtures.task("a", 60, 0, at(9, 0), at(21, 0)),
                ProblemFixtures.task("b", 60, 1, at(9, 0), at(21, 0)));
        TravelMatrix matrix = flatMatrix(items, window(9, 21), 10);

        ScheduleScore kept = engine.search(new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), matrix, preferences(true, WeatherContext.unavailable())),
                SearchBudget.defaultBudget()).getPlan().getScore();
        ScheduleScore free = engine.search(new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), matrix, preferences(false, WeatherContext.unavailable())),
                SearchBudget.defaultBudget()).getPlan().getScore();

        assertEquals(0, free.getOrderPenaltyMinutes(),
                "with the preference off, order costs nothing");
        assertTrue(kept.getOrderPenaltyMinutes() <= SchedulingPreferences.MAX_ORDER_PENALTY_MINUTES);
    }

    @Test
    void theOrderChargeCannotOutweighAGenuinelyBetterDay() {
        // Reversing the order saves far more travel than the capped order charge.
        ScheduleTask first = ProblemFixtures.task("first", 60, 0, at(9, 0), at(21, 0));
        ScheduleTask second = ProblemFixtures.task("second", 60, 1, at(9, 0), at(21, 0));
        List<ScheduleTask> items = tasks(first, second);

        PeriodPlan plan = PeriodPlan.forRun(window(9, 21), false, 2);
        TravelMatrix.Builder builder = TravelMatrix.builder(plan);
        for (DeparturePeriod period : plan.activePeriods()) {
            builder.put("first", "second", period, TravelEstimate.routed(200));
            builder.put("second", "first", period, TravelEstimate.routed(5));
        }
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items, noBlockedWindows(),
                builder.build(), preferences(true, WeatherContext.unavailable()));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertEquals(Arrays.asList("second", "first"), result.getPlan().orderedEventIds(),
                "saving 195 minutes must beat a charge capped at "
                        + SchedulingPreferences.MAX_ORDER_PENALTY_MINUTES);
    }

    @Test
    void theActiveObjectiveListAlwaysIncludesTheBuiltInsAndReflectsTheChoice() {
        assertEquals(Arrays.asList(PolicyId.WEATHER, PolicyId.MEAL_TIME, PolicyId.DAYLIGHT,
                        PolicyId.REDUCE_IDLE, PolicyId.PRESERVE_ORDER),
                preferences(true, WeatherContext.unavailable()).activeIds());
        assertEquals(Arrays.asList(PolicyId.WEATHER, PolicyId.MEAL_TIME, PolicyId.DAYLIGHT,
                        PolicyId.REDUCE_IDLE),
                preferences(false, WeatherContext.unavailable()).activeIds());
    }

    @Test
    void theSameInputAlwaysProducesTheSameSchedule() {
        List<ScheduleTask> items = tasks(
                ProblemFixtures.task("a", 60, 0, at(9, 0), at(21, 0)),
                food("b", 60, 1),
                outdoor("c", 60, 2));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items, noBlockedWindows(),
                flatMatrix(items, window(9, 21), 12),
                preferences(true, WeatherContext.unavailable()));

        ScheduleSearchResult first = engine.search(problem, SearchBudget.defaultBudget());
        ScheduleSearchResult second = engine.search(problem, SearchBudget.defaultBudget());

        assertEquals(first.getPlan().orderedEventIds(), second.getPlan().orderedEventIds());
        assertEquals(first.getPlan().getScore(), second.getPlan().getScore());
    }

    @Test
    void aSoftPreferenceNeverOverridesOpeningHours() {
        ScheduleTask lateOnly = ScheduleTask.movable("supper",
                ProblemFixtures.activity("supper", ActivityCategory.FOOD,
                        IndoorOutdoorType.INDOOR, at(15, 0), at(16, 30)), 90, 0);
        List<ScheduleTask> items = tasks(lateOnly);
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items, noBlockedWindows(),
                flatMatrix(items, window(9, 21), 10),
                preferences(false, WeatherContext.unavailable()));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound(), "a preference must not make a day unschedulable");
        assertEquals(at(15, 0), result.getPlan().getPlacements().get(0).getStart());
    }

    @Test
    void policiesStillEmitReasonsForTheSchedulesTheyJudge() {
        MealWindowPolicy policy = new MealWindowPolicy();
        ScheduleTask lunch = food("lunch", 60, 0);

        assertEquals(ReasonCode.IN_MEAL_WINDOW,
                policy.reasonFor(placedAt(lunch, 12, 30), PolicyContext.empty()).getCode());
        assertEquals(ReasonCode.OUTSIDE_MEAL_WINDOW,
                policy.reasonFor(placedAt(lunch, 16, 0), PolicyContext.empty()).getCode());
    }
}
