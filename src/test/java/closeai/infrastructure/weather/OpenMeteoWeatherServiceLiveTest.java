package closeai.infrastructure.weather;

import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit opt-in smoke test; normal unit tests never depend on the public internet. */
final class OpenMeteoWeatherServiceLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_OPEN_METEO_TEST", matches = "true")
    void fetchesARealForecast() {
        LocalDate date = LocalDate.now().plusDays(1);
        Trip trip = new Trip("live-weather", "Toronto", date, LocalTime.NOON,
                LocalTime.of(18, 0), TransportationMode.WALKING);

        WeatherWarning warning = new OpenMeteoWeatherService().getWarning(trip);

        assertNotNull(warning.getWeatherCondition());
        assertNotNull(warning.getSeverity());
        assertTrue(Double.isFinite(warning.getLocation().getLatitude()));
    }
}
