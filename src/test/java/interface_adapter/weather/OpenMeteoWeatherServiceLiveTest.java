package interface_adapter.weather;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.TransportationMode;

/** Explicit opt-in smoke test; normal unit tests never depend on the public internet. */
final class OpenMeteoWeatherServiceLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_OPEN_METEO_TEST", matches = "true")
    void fetchesARealForecast() {
        final LocalDate date = LocalDate.now().plusDays(1);
        final Trip trip = new Trip("live-weather", "Toronto", date, LocalTime.NOON,
                LocalTime.of(18, 0), TransportationMode.WALKING);

        final WeatherWarning warning = new OpenMeteoWeatherService().getWarning(trip);

        assertNotNull(warning.getWeatherCondition());
        assertNotNull(warning.getSeverity());
        assertTrue(Double.isFinite(warning.getLocation().getLatitude()));
    }
}
