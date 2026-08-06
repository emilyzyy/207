package closeai.adapters.gateways;

import closeai.application.autoschedule.WeatherContext;
import closeai.application.autoschedule.WeatherContextGateway;
import closeai.application.ports.WeatherService;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;

/**
 * Supplies Autoschedule's forecast context from the team's shared {@code WeatherService}.
 *
 * <p>That service reports one warning for the whole trip, so the context produced here
 * carries a single severity. Every candidate time then scores alike, which means weather
 * mostly warns rather than moves activities — stated plainly rather than dressed up.
 * Once an hourly forecast is available the same gateway can return an hourly context and
 * the policy will start relocating outdoor activities with no change to the engine.</p>
 *
 * <p>Any failure becomes an unavailable context rather than an exception. Weather is a
 * preference, and losing the forecast must never cost the traveller their schedule.</p>
 */
public final class WeatherServiceContextGateway implements WeatherContextGateway {

    private final WeatherService weatherService;

    public WeatherServiceContextGateway(WeatherService weatherService) {
        if (weatherService == null) {
            throw new IllegalArgumentException("Weather service is required");
        }
        this.weatherService = weatherService;
    }

    @Override
    public WeatherContext contextFor(Trip trip) {
        try {
            WeatherWarning warning = weatherService.getWarning(trip);
            if (warning == null || warning.getSeverity() == null) {
                return WeatherContext.unavailable();
            }
            return WeatherContext.tripLevel(warning.getSeverity());
        } catch (RuntimeException exception) {
            return WeatherContext.unavailable();
        }
    }
}
