package use_case.ports;

import java.time.Duration;
import java.util.List;

import entity.entities.Trip;
import entity.entities.WeatherWarning;

/** Supplies a trip-date forecast without exposing HTTP or provider details to the application. */
public interface WeatherService {
    /**
     * Returns the available hourly points for the trip date in chronological order.
     * @param trip the t ri p value
     * @return the result of the operation
     */
    List<WeatherWarning> getHourlyWarnings(Trip trip);

    /**
     * Preserves the dashboard preview by selecting the hour nearest the trip start.
     * @param trip the t ri p value
     * @return the result of the operation
     */
    default WeatherWarning getWarning(Trip trip) {
        if (trip == null) {
            throw new IllegalArgumentException("Trip is required");
        }
        final List<WeatherWarning> hourly = getHourlyWarnings(trip);
        if (hourly == null || hourly.isEmpty()) {
            throw new IllegalStateException("Weather service returned no hourly forecast");
        }
        WeatherWarning closest = null;
        long closestMinutes = Long.MAX_VALUE;
        for (WeatherWarning warning : hourly) {
            if (warning == null || warning.getTime() == null) {
                continue;
            }
            final long difference = Math.abs(Duration.between(
                    trip.getStartTime(), warning.getTime()).toMinutes());
            if (difference < closestMinutes) {
                closest = warning;
                closestMinutes = difference;
            }
        }
        if (closest == null) {
            throw new IllegalStateException("Weather service returned no usable hourly forecast");
        }
        return closest;
    }
}
