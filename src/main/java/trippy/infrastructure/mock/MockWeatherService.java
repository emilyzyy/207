package trippy.infrastructure.mock;

import trippy.application.ports.WeatherService;
import trippy.domain.entities.Trip;
import trippy.domain.entities.WeatherWarning;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.WeatherSeverity;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MockWeatherService implements WeatherService {
    @Override
    public List<WeatherWarning> getHourlyWarnings(Trip trip) {
        if (trip == null) throw new IllegalArgumentException("Trip is required");
        Location location = new Location(43.6532, -79.3832, trip.getDestination());
        List<WeatherWarning> hourly = new ArrayList<WeatherWarning>();
        for (int hour = 0; hour < 24; hour++) {
            LocalTime time = LocalTime.of(hour, 0);
            hourly.add(new WeatherWarning(location, time, "Sunny intervals", WeatherSeverity.LOW,
                    "24°C · 10% precipitation · 8.0 km/h wind · low conditions."));
        }
        return Collections.unmodifiableList(hourly);
    }
}
