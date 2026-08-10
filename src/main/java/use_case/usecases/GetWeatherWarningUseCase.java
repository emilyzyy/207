package use_case.usecases;

import java.util.List;

import entity.entities.Trip;
import entity.entities.WeatherWarning;
import use_case.ports.TripRepository;
import use_case.ports.WeatherService;

public final class GetWeatherWarningUseCase {
    private final TripRepository trips;
    private final WeatherService weather;

    public GetWeatherWarningUseCase(TripRepository trips, WeatherService weather) {
        this.trips = trips;
        this.weather = weather;
    }

    /**
     * Performs the e xe cu te operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public WeatherWarning execute(String tripId) {
        final Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        return weather.getWarning(trip);
    }

    /**
     * Performs the e xe cu te ho ur ly operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public List<WeatherWarning> executeHourly(String tripId) {
        final Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        return weather.getHourlyWarnings(trip);
    }
}
