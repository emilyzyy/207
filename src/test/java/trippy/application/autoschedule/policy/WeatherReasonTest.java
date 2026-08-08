package trippy.application.autoschedule.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import trippy.application.autoschedule.PlacedActivity;
import trippy.application.autoschedule.PolicyContext;
import trippy.application.autoschedule.ProblemFixtures;
import trippy.application.autoschedule.Reason;
import trippy.application.autoschedule.ReasonCode;
import trippy.application.autoschedule.ScheduleTask;
import trippy.application.autoschedule.WeatherContext;
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.IndoorOutdoorType;
import trippy.domain.valueobjects.WeatherSeverity;

/**
 * An outdoor activity is charged a small penalty even in perfect weather, which is
 * deliberate and harmless to the ranking because it is constant across candidate times.
 * It is not, however, something to tell the user about: "poorer weather expected outdoors"
 * over a clear forecast is simply false, and it used to appear on the very row that had
 * just earned "Moved to better weather".
 */
class WeatherReasonTest {

    private final WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();

    private static PolicyContext at(WeatherSeverity severity) {
        Map<Integer, WeatherSeverity> byHour = new HashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            byHour.put(hour, severity);
        }
        return new PolicyContext(WeatherContext.hourly(byHour));
    }

    private static PlacedActivity outdoorAt(int hour) {
        ScheduleTask task = ScheduleTask.movable("park",
                ProblemFixtures.activity("park", ActivityCategory.PARKS_NATURE,
                        IndoorOutdoorType.OUTDOOR, LocalTime.of(6, 0), LocalTime.of(23, 0)),
                60, 0);
        return PlacedActivity.first(task, LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0), 0, 0);
    }

    @Test
    void goodWeatherProducesNoReasonEvenThoughItStillCostsAFewMinutes() {
        PlacedActivity placed = outdoorAt(11);

        assertTrue(policy.penaltyMinutes(placed, at(WeatherSeverity.LOW)) > 0,
                "the flat outdoor charge is intentional and still applies");
        assertNull(policy.reasonFor(placed, at(WeatherSeverity.LOW)),
                "but a clear forecast is not a warning worth showing");
    }

    @Test
    void poorWeatherStillExplainsItself() {
        for (WeatherSeverity severity
                : new WeatherSeverity[] {WeatherSeverity.MEDIUM, WeatherSeverity.HIGH}) {
            Reason reason = policy.reasonFor(outdoorAt(11), at(severity));
            assertEquals(ReasonCode.WEATHER_EXPOSURE, reason.getCode(), severity.name());
            assertEquals(severity.name(), reason.getDetail(), severity.name());
        }
    }

    @Test
    void anIndoorActivityIsNeverGivenAWeatherReason() {
        ScheduleTask indoor = ScheduleTask.movable("museum",
                ProblemFixtures.activity("museum", ActivityCategory.MUSEUM,
                        IndoorOutdoorType.INDOOR, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                60, 0);
        PlacedActivity placed = PlacedActivity.first(indoor, LocalTime.of(11, 0),
                LocalTime.of(12, 0), 0, 0);

        assertNull(policy.reasonFor(placed, at(WeatherSeverity.HIGH)));
    }
}
