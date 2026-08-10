package use_case.usecases;

import use_case.ports.TripRepository;
import use_case.ports.WeatherService;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import java.util.List;

public final class GetWeatherWarningUseCase {
    private final TripRepository trips;
    private final WeatherService weather;
    public GetWeatherWarningUseCase(TripRepository trips, WeatherService weather) {
        this.trips = trips; this.weather = weather;
    }
    public WeatherWarning execute(String tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        return weather.getWarning(trip);
    }

    public List<WeatherWarning> executeHourly(String tripId) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        return weather.getHourlyWarnings(trip);
    }
}
