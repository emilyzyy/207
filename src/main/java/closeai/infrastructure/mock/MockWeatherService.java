package closeai.infrastructure.mock;

import closeai.application.ports.WeatherService;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.WeatherSeverity;

public final class MockWeatherService implements WeatherService {
    public WeatherWarning getWarning(Trip trip) {
        return new WeatherWarning(new Location(43.6532, -79.3832, trip.getDestination()),
                trip.getStartTime(), "Sunny intervals", WeatherSeverity.LOW,
                "24°C · Good conditions for a city day. Bring water for outdoor stops.");
    }
}
