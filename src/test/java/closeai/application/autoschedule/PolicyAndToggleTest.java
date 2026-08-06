package closeai.application.autoschedule;

import static closeai.application.autoschedule.ProblemFixtures.at;
import static closeai.application.autoschedule.ProblemFixtures.flatMatrix;
import static closeai.application.autoschedule.ProblemFixtures.noBlockedWindows;
import static closeai.application.autoschedule.ProblemFixtures.tasks;
import static closeai.application.autoschedule.ProblemFixtures.window;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyAndToggleTest {

    private static final List<SoftPolicy> REGISTERED = Arrays.asList(
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

    @Test
    void mealsScoreBestInsideACustomaryWindow() {
        MealWindowPolicy policy = new MealWindowPolicy();
        ScheduleTask lunch = food("lunch", 60, 0);

        assertEquals(0, policy.penaltyMinutes(placedAt(lunch, 12, 30), PolicyContext.empty()));
        assertTrue(policy.penaltyMinutes(placedAt(lunch, 15, 30), PolicyContext.empty()) > 0);
    }

    @Test
    void mealPolicyIgnoresActivitiesThatAreNotFood() {
        MealWindowPolicy policy = new MealWindowPolicy();
        ScheduleTask museum = ProblemFixtures.task("museum", 60, 0, at(9, 0), at(21, 0));

        assertEquals(0, policy.penaltyMinutes(placedAt(museum, 15, 30), PolicyContext.empty()));
        assertNull(policy.reasonFor(placedAt(museum, 15, 30), PolicyContext.empty()));
    }

    @Test
    void outdoorActivitiesScoreBestInDaylight() {
        DaylightPolicy policy = new DaylightPolicy();
        ScheduleTask park = outdoor("park", 60, 0);

        assertEquals(0, policy.penaltyMinutes(placedAt(park, 14, 0), PolicyContext.empty()));
        assertTrue(policy.penaltyMinutes(placedAt(park, 20, 0), PolicyContext.empty()) > 0);
    }

    @Test
    void weatherPenalisesExposureAndScalesWithSeverity() {
        WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();
        ScheduleTask park = outdoor("park", 60, 0);
        PlacedActivity placement = placedAt(park, 14, 0);

        int low = policy.penaltyMinutes(placement,
                new PolicyContext(WeatherContext.tripLevel(WeatherSeverity.LOW)));
        int high = policy.penaltyMinutes(placement,
                new PolicyContext(WeatherContext.tripLevel(WeatherSeverity.HIGH)));

        assertTrue(low > 0);
        assertTrue(high > low, "worse weather should cost more");
    }

    @Test
    void indoorActivitiesAreUnaffectedByWeather() {
        WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();
        ScheduleTask museum = ProblemFixtures.task("museum", 60, 0, at(9, 0), at(21, 0));

        assertEquals(0, policy.penaltyMinutes(placedAt(museum, 14, 0),
                new PolicyContext(WeatherContext.tripLevel(WeatherSeverity.HIGH))));
    }

    @Test
    void missingForecastCostsNothingAndSaysNothing() {
        WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();
        ScheduleTask park = outdoor("park", 60, 0);
        PolicyContext noForecast = new PolicyContext(WeatherContext.unavailable());

        assertEquals(0, policy.penaltyMinutes(placedAt(park, 14, 0), noForecast));
        assertNull(policy.reasonFor(placedAt(park, 14, 0), noForecast));
    }

    @Test
    void anHourlyForecastLetsWeatherVaryAcrossTheDay() {
        java.util.Map<Integer, WeatherSeverity> byHour = new java.util.HashMap<>();
        byHour.put(10, WeatherSeverity.LOW);
        byHour.put(15, WeatherSeverity.HIGH);
        PolicyContext context = new PolicyContext(WeatherContext.hourly(byHour));
        WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();
        ScheduleTask park = outdoor("park", 60, 0);

        assertTrue(policy.penaltyMinutes(placedAt(park, 15, 0), context)
                > policy.penaltyMinutes(placedAt(park, 10, 0), context));
    }

    @Test
    void switchingAPolicyOffRemovesItsInfluenceEntirely() {
        List<ScheduleTask> items = tasks(food("lunch", 60, 0),
                ProblemFixtures.task("museum", 60, 1, at(9, 0), at(21, 0)));
        TravelMatrix matrix = flatMatrix(items, window(9, 21), 10);

        ScheduleScore withMeal = scoreOf(items, matrix, EnumSet.of(PolicyId.MEAL_TIME));
        ScheduleScore withoutMeal = scoreOf(items, matrix, EnumSet.noneOf(PolicyId.class));

        assertTrue(withMeal.getPolicyPenalty() >= 0);
        assertEquals(0, withoutMeal.getPolicyPenalty(),
                "a disabled policy must contribute nothing at all");
    }

    @Test
    void switchingIdleOffRemovesTheIdleTier() {
        List<ScheduleTask> items = tasks(
                ProblemFixtures.task("a", 60, 0, at(9, 0), at(21, 0)),
                ProblemFixtures.task("b", 60, 1, at(15, 0), at(21, 0)));
        TravelMatrix matrix = flatMatrix(items, window(9, 21), 10);

        assertTrue(scoreOf(items, matrix, EnumSet.of(PolicyId.REDUCE_IDLE))
                .getAvoidableIdleMinutes() >= 0);
        assertEquals(0, scoreOf(items, matrix, EnumSet.noneOf(PolicyId.class))
                .getAvoidableIdleMinutes());
    }

    @Test
    void switchingOrderPreservationOffRemovesTheOrderTier() {
        List<ScheduleTask> items = tasks(
                ProblemFixtures.task("a", 60, 0, at(9, 0), at(21, 0)),
                ProblemFixtures.task("b", 60, 1, at(9, 0), at(21, 0)));
        TravelMatrix matrix = flatMatrix(items, window(9, 21), 10);

        assertEquals(0, scoreOf(items, matrix, EnumSet.noneOf(PolicyId.class))
                .getOrderDisruption());
    }

    @Test
    void aSoftPreferenceNeverOverridesOpeningHours() {
        // The only slot that fits is far from any meal window; it must still be used.
        ScheduleTask lateOnly = ScheduleTask.movable("supper",
                ProblemFixtures.activity("supper", ActivityCategory.FOOD,
                        IndoorOutdoorType.INDOOR, at(15, 0), at(16, 30)), 90, 0);
        List<ScheduleTask> items = tasks(lateOnly);
        ScheduleProblem problem = problemFor(items, flatMatrix(items, window(9, 21), 10),
                EnumSet.of(PolicyId.MEAL_TIME));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound(), "a soft preference must not make a day unschedulable");
        assertEquals(at(15, 0), result.getPlan().getPlacements().get(0).getStart());
    }

    @Test
    void policiesEmitReasonsOnlyForTheSchedulesTheyJudge() {
        MealWindowPolicy policy = new MealWindowPolicy();
        ScheduleTask lunch = food("lunch", 60, 0);

        Reason good = policy.reasonFor(placedAt(lunch, 12, 30), PolicyContext.empty());
        Reason bad = policy.reasonFor(placedAt(lunch, 16, 0), PolicyContext.empty());

        assertNotNull(good);
        assertEquals(ReasonCode.IN_MEAL_WINDOW, good.getCode());
        assertNotNull(bad);
        assertEquals(ReasonCode.OUTSIDE_MEAL_WINDOW, bad.getCode());
        assertNotEquals(good.getCode(), bad.getCode());
    }

    @Test
    void enablingAPolicyChangesNothingAboutTheEngineItself() {
        // The same engine instance handles both runs; only its input differs.
        List<ScheduleTask> items = tasks(food("lunch", 60, 0),
                ProblemFixtures.task("museum", 60, 1, at(9, 0), at(21, 0)));
        TravelMatrix matrix = flatMatrix(items, window(9, 21), 10);

        ScheduleSearchResult off = engine.search(
                problemFor(items, matrix, EnumSet.noneOf(PolicyId.class)),
                SearchBudget.defaultBudget());
        ScheduleSearchResult on = engine.search(
                problemFor(items, matrix, EnumSet.of(PolicyId.MEAL_TIME)),
                SearchBudget.defaultBudget());

        assertTrue(off.isFound());
        assertTrue(on.isFound());
    }

    @Test
    void activePolicyListReportsExactlyWhatWasEnabled() {
        SchedulingPreferences preferences = SchedulingPreferences.select(REGISTERED,
                EnumSet.of(PolicyId.WEATHER, PolicyId.REDUCE_IDLE), PolicyContext.empty());

        assertEquals(Arrays.asList(PolicyId.WEATHER, PolicyId.REDUCE_IDLE),
                preferences.activeIds());
    }

    private ScheduleProblem problemFor(List<ScheduleTask> items, TravelMatrix matrix,
                                       Set<PolicyId> enabled) {
        return new ScheduleProblem(window(9, 21), items, noBlockedWindows(), matrix,
                SchedulingPreferences.select(REGISTERED, enabled,
                        new PolicyContext(WeatherContext.tripLevel(WeatherSeverity.MEDIUM))));
    }

    private ScheduleScore scoreOf(List<ScheduleTask> items, TravelMatrix matrix,
                                  Set<PolicyId> enabled) {
        ScheduleSearchResult result = engine.search(problemFor(items, matrix, enabled),
                SearchBudget.defaultBudget());
        assertTrue(result.isFound());
        return result.getPlan().getScore();
    }
}
