package closeai.application.usecases;

import closeai.application.ports.TripRepository;
import closeai.application.ports.WeatherService;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;

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
}
