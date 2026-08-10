package use_case.autoschedule;

import static use_case.autoschedule.ProblemFixtures.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.WeatherSeverity;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The rule that keeps the improvement cards honest: an improvement is a <em>change</em>.
 *
 * <p>Most of these tests exist to prove a card does <em>not</em> appear. That is the
 * interesting direction — a claim that never fires when it should is a missing feature, but
 * a claim that fires when it should not is a lie on screen, and the second is worse.</p>
 */
class ScheduleImprovementFinderTest {

    private final ScheduleImprovementFinder finder = new ScheduleImprovementFinder();

    private static final List<SoftPolicy> POLICIES = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private static Activity activityOf(String id, ActivityCategory category,
                                       IndoorOutdoorType exposure) {
        return new Activity(id, id, category, new Location(43.65, -79.38, id), 4.5, 60,
                at(0, 0), at(23, 59), exposure, "none");
    }

    private static ScheduledEvent original(String id, ActivityCategory category,
                                           IndoorOutdoorType exposure, int startHour) {
        Activity activity = activityOf(id, category, exposure);
        return new ScheduledEvent(id, activity, at(startHour, 0), at(startHour + 1, 0),
                EventType.ACTIVITY, "");
    }

    private static SchedulePlan planWith(String id, ActivityCategory category,
                                         IndoorOutdoorType exposure, int startHour,
                                         boolean locked) {
        Activity activity = activityOf(id, category, exposure);
        ScheduleTask task = locked
                ? new ScheduleTask(id, activity, 60, 0,
                        new TimeWindow(at(startHour, 0), at(startHour + 1, 0)))
                : ScheduleTask.movable(id, activity, 60, 0);
        PlacedActivity placed = PlacedActivity.first(task, at(startHour, 0),
                at(startHour + 1, 0), 0, 0);
        return new SchedulePlan(Collections.singletonList(placed),
                new ScheduleScore(0, 0, 0, 0, ""));
    }

    private static SchedulingPreferences preferences(WeatherContext weather) {
        return SchedulingPreferences.builtIn(POLICIES, false, new PolicyContext(weather));
    }

    private static Map<Integer, WeatherSeverity> forecast(WeatherSeverity morning,
                                                          WeatherSeverity evening) {
        Map<Integer, WeatherSeverity> byHour = new HashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            byHour.put(hour, hour >= 18 ? evening : morning);
        }
        return byHour;
    }

    private static boolean has(List<ScheduleImprovement> improvements,
                               ScheduleImprovementType type) {
        return improvements.stream().anyMatch(i -> i.getType() == type);
    }

    private static ScheduleImprovement find(List<ScheduleImprovement> improvements,
                                            ScheduleImprovementType type) {
        return improvements.stream().filter(i -> i.getType() == type).findFirst().orElse(null);
    }

    // --- whole-schedule figures --------------------------------------------------------

    @Test
    void waitingAndTravelAreReportedOnlyWhenTheyActuallyFell() {
        SchedulePlan plan = planWith("a", ActivityCategory.MUSEUM,
                IndoorOutdoorType.INDOOR, 10, false);
        List<ScheduledEvent> before = Collections.singletonList(
                original("a", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR, 10));

        List<ScheduleImprovement> improved = finder.find(before, plan,
                preferences(WeatherContext.unavailable()), metrics(120, 200), 100, 50);
        assertEquals(150, find(improved, ScheduleImprovementType.WAITING_REDUCED).getAmount());
        assertEquals(20, find(improved, ScheduleImprovementType.TRAVEL_REDUCED).getAmount());

        List<ScheduleImprovement> worse = finder.find(before, plan,
                preferences(WeatherContext.unavailable()), metrics(100, 50), 120, 200);
        assertFalse(has(worse, ScheduleImprovementType.WAITING_REDUCED),
                "waiting grew, so there is nothing to celebrate");
        assertFalse(has(worse, ScheduleImprovementType.TRAVEL_REDUCED),
                "travel grew, so there is nothing to celebrate");
    }

    private static ScheduleMetrics metrics(int travel, int idle) {
        // ofExistingSchedule is the only public route in, so build a matching schedule.
        return new ScheduleMetricsStub(travel, idle).asMetrics();
    }

    /** Small shim so the test can state before-totals directly. */
    private static final class ScheduleMetricsStub {
        private final int travel;
        private final int idle;

        ScheduleMetricsStub(int travel, int idle) {
            this.travel = travel;
            this.idle = idle;
        }

        ScheduleMetrics asMetrics() {
            List<ScheduledEvent> events = Arrays.asList(
                    new ScheduledEvent("t", null, at(0, 0), at(0, 0).plusMinutes(travel),
                            EventType.TRAVEL, "travel"),
                    new ScheduledEvent("x", activityOf("x", ActivityCategory.MUSEUM,
                            IndoorOutdoorType.INDOOR),
                            at(0, 0).plusMinutes(travel + idle),
                            at(0, 0).plusMinutes(travel + idle + 60),
                            EventType.ACTIVITY, ""));
            return ScheduleMetrics.ofExistingSchedule(events);
        }
    }

    // --- daylight: a change, not a final state -----------------------------------------

    @Test
    void anOutdoorActivityMovedFromDarknessIntoDaylightIsAnImprovement() {
        List<ScheduledEvent> before = Collections.singletonList(
                original("park", ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR, 21));
        SchedulePlan after = planWith("park", ActivityCategory.PARKS_NATURE,
                IndoorOutdoorType.OUTDOOR, 13, false);

        List<ScheduleImprovement> improvements = finder.find(before, after,
                preferences(WeatherContext.unavailable()), null, 0, 0);

        ScheduleImprovement daylight = find(improvements,
                ScheduleImprovementType.MOVED_INTO_DAYLIGHT);
        assertTrue(daylight != null, "9pm to 1pm is a real daylight gain: " + improvements);
        assertEquals("park", daylight.getSubject(), "the card names the activity it is about");
    }

    @Test
    void anActivityAlreadyInDaylightEarnsNoDaylightCard() {
        List<ScheduledEvent> before = Collections.singletonList(
                original("park", ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR, 11));
        SchedulePlan after = planWith("park", ActivityCategory.PARKS_NATURE,
                IndoorOutdoorType.OUTDOOR, 14, false);

        List<ScheduleImprovement> improvements = finder.find(before, after,
                preferences(WeatherContext.unavailable()), null, 0, 0);

        assertFalse(has(improvements, ScheduleImprovementType.MOVED_INTO_DAYLIGHT),
                "it was in daylight before and is in daylight now; nothing was improved");
    }

    @Test
    void anActivityPushedOutOfDaylightEarnsNoCard() {
        List<ScheduledEvent> before = Collections.singletonList(
                original("park", ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR, 13));
        SchedulePlan after = planWith("park", ActivityCategory.PARKS_NATURE,
                IndoorOutdoorType.OUTDOOR, 21, false);

        List<ScheduleImprovement> improvements = finder.find(before, after,
                preferences(WeatherContext.unavailable()), null, 0, 0);

        assertFalse(has(improvements, ScheduleImprovementType.MOVED_INTO_DAYLIGHT),
                "this got worse, and a worse outcome must never appear as an achievement");
    }

    // --- weather: needs demonstrably milder conditions ---------------------------------

    @Test
    void anOutdoorActivityMovedIntoMilderWeatherIsAnImprovement() {
        WeatherContext hourly = WeatherContext.hourly(
                forecast(WeatherSeverity.LOW, WeatherSeverity.HIGH));
        List<ScheduledEvent> before = Collections.singletonList(
                original("park", ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR, 19));
        SchedulePlan after = planWith("park", ActivityCategory.PARKS_NATURE,
                IndoorOutdoorType.OUTDOOR, 13, false);

        List<ScheduleImprovement> improvements =
                finder.find(before, after, preferences(hourly), null, 0, 0);

        assertTrue(has(improvements, ScheduleImprovementType.MOVED_TO_BETTER_WEATHER),
                "HIGH severity to LOW is demonstrably better: " + improvements);
    }

    @Test
    void anUnchangedForecastEarnsNoWeatherCard() {
        WeatherContext flat = WeatherContext.hourly(
                forecast(WeatherSeverity.LOW, WeatherSeverity.LOW));
        List<ScheduledEvent> before = Collections.singletonList(
                original("park", ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR, 19));
        SchedulePlan after = planWith("park", ActivityCategory.PARKS_NATURE,
                IndoorOutdoorType.OUTDOOR, 13, false);

        List<ScheduleImprovement> improvements =
                finder.find(before, after, preferences(flat), null, 0, 0);

        assertFalse(has(improvements, ScheduleImprovementType.MOVED_TO_BETTER_WEATHER),
                "the weather was the same at both times, so moving it improved nothing");
    }

    @Test
    void aWholeDayForecastCannotProduceAWeatherCard() {
        List<ScheduledEvent> before = Collections.singletonList(
                original("park", ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR, 19));
        SchedulePlan after = planWith("park", ActivityCategory.PARKS_NATURE,
                IndoorOutdoorType.OUTDOOR, 13, false);

        List<ScheduleImprovement> improvements = finder.find(before, after,
                preferences(WeatherContext.tripLevel(WeatherSeverity.HIGH)), null, 0, 0);

        assertFalse(has(improvements, ScheduleImprovementType.MOVED_TO_BETTER_WEATHER),
                "a forecast that scores every hour alike cannot prove an hour was better");
    }

    // --- meals -------------------------------------------------------------------------

    @Test
    void aMealMovedTowardItsWindowIsAnImprovementAndNamesTheActivity() {
        List<ScheduledEvent> before = Collections.singletonList(
                original("lunch", ActivityCategory.FOOD, IndoorOutdoorType.INDOOR, 16));
        SchedulePlan after = planWith("lunch", ActivityCategory.FOOD,
                IndoorOutdoorType.INDOOR, 12, false);

        List<ScheduleImprovement> improvements = finder.find(before, after,
                preferences(WeatherContext.unavailable()), null, 0, 0);

        ScheduleImprovement meal = find(improvements,
                ScheduleImprovementType.MEAL_MOVED_TOWARD_WINDOW);
        assertTrue(meal != null, "4pm to noon is a real meal improvement: " + improvements);
        assertEquals("lunch", meal.getSubject());
    }

    @Test
    void aMealAlreadyInItsWindowEarnsNoCard() {
        List<ScheduledEvent> before = Collections.singletonList(
                original("lunch", ActivityCategory.FOOD, IndoorOutdoorType.INDOOR, 12));
        SchedulePlan after = planWith("lunch", ActivityCategory.FOOD,
                IndoorOutdoorType.INDOOR, 12, false);

        assertFalse(has(finder.find(before, after, preferences(WeatherContext.unavailable()),
                null, 0, 0), ScheduleImprovementType.MEAL_MOVED_TOWARD_WINDOW));
    }

    // --- locks -------------------------------------------------------------------------

    @Test
    void aPinHonouredIsAnImprovementAndAPinIsNotClaimedWhenTheTimeChanged() {
        List<ScheduledEvent> before = Collections.singletonList(
                original("museum", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR, 11));

        List<ScheduleImprovement> kept = finder.find(before,
                planWith("museum", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR, 11, true),
                preferences(WeatherContext.unavailable()), null, 0, 0);
        assertEquals("museum",
                find(kept, ScheduleImprovementType.LOCK_PRESERVED).getSubject());

        List<ScheduleImprovement> unlocked = finder.find(before,
                planWith("museum", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR, 11, false),
                preferences(WeatherContext.unavailable()), null, 0, 0);
        assertFalse(has(unlocked, ScheduleImprovementType.LOCK_PRESERVED),
                "an activity that merely stayed put was never pinned");
    }

    // --- order: the actual sequence, not the preference --------------------------------

    @Test
    void orderPreservedComparesTheRealSequenceRatherThanThePreference() {
        Activity first = activityOf("a", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR);
        Activity second = activityOf("b", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR);
        List<ScheduledEvent> before = Arrays.asList(
                new ScheduledEvent("a", first, at(9, 0), at(10, 0), EventType.ACTIVITY, ""),
                new ScheduledEvent("b", second, at(11, 0), at(12, 0), EventType.ACTIVITY, ""));

        SchedulePlan sameOrder = new SchedulePlan(Arrays.asList(
                PlacedActivity.first(ScheduleTask.movable("a", first, 60, 0),
                        at(13, 0), at(14, 0), 0, 0),
                PlacedActivity.first(ScheduleTask.movable("b", second, 60, 0),
                        at(15, 0), at(16, 0), 0, 0)),
                new ScheduleScore(0, 0, 0, 0, ""));
        SchedulePlan swapped = new SchedulePlan(Arrays.asList(
                PlacedActivity.first(ScheduleTask.movable("b", second, 60, 0),
                        at(13, 0), at(14, 0), 0, 0),
                PlacedActivity.first(ScheduleTask.movable("a", first, 60, 0),
                        at(15, 0), at(16, 0), 0, 0)),
                new ScheduleScore(0, 0, 0, 0, ""));

        // The preference is false in both calls, proving the card follows the outcome.
        assertTrue(has(finder.find(before, sameOrder,
                        preferences(WeatherContext.unavailable()), null, 0, 0),
                ScheduleImprovementType.ORDER_PRESERVED),
                "both activities kept their relative order even though times changed");
        assertFalse(has(finder.find(before, swapped,
                        preferences(WeatherContext.unavailable()), null, 0, 0),
                ScheduleImprovementType.ORDER_PRESERVED),
                "the order actually changed, so the claim would be false");
    }
}
