package closeai.application.scheduling;

import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.WeatherSeverity;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultActivityScoringPolicyTest {
    private final DefaultActivityScoringPolicy policy = new DefaultActivityScoringPolicy();

    @Test
    void combinesRatingTravelWeatherAndExposure() {
        Activity indoor = activity("indoor", 4.5, IndoorOutdoorType.INDOOR);
        Activity mixed = activity("mixed", 4.5, IndoorOutdoorType.MIXED);
        Activity outdoor = activity("outdoor", 4.5, IndoorOutdoorType.OUTDOOR);

        assertEquals(8.5, policy.score(indoor, 10, WeatherSeverity.HIGH), 0.0001);
        assertEquals(6.5, policy.score(mixed, 10, WeatherSeverity.HIGH), 0.0001);
        assertEquals(4.5, policy.score(outdoor, 10, WeatherSeverity.HIGH), 0.0001);
        assertTrue(policy.score(outdoor, 10, WeatherSeverity.LOW)
                > policy.score(outdoor, 40, WeatherSeverity.LOW));
    }

    private Activity activity(String id, double rating, IndoorOutdoorType type) {
        return new Activity(id, id, ActivityCategory.ATTRACTION,
                new Location(43.0, -79.0, id), rating, 60,
                LocalTime.of(8, 0), LocalTime.of(20, 0), type, "test");
    }
}
