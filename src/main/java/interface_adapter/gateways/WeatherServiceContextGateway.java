package interface_adapter.gateways;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.WeatherSeverity;
import use_case.autoschedule.WeatherContext;
import use_case.autoschedule.WeatherContextGateway;
import use_case.ports.WeatherService;

/**
 * Supplies Autoschedule's forecast context from the team's shared {@code WeatherService}.
 *
 * <p>Since Shiyuan added {@code getHourlyWarnings}, this asks for the hour-by-hour forecast
 * and turns it into an hourly {@link WeatherContext}. That is what finally lets weather
 * influence <em>when</em> an outdoor activity is scheduled, and it is why the "Consider
 * weather" preference can now be offered at all: the capability gate reads
 * {@code canDistinguishTimes()}, which is true exactly when more than one hour is known.</p>
 *
 * <p>Nothing in the engine, the Interactor or the UI changed to make that happen. The
 * hourly shape was accepted by {@code WeatherContext} from the start, and this adapter is
 * the single place that had to learn the new provider method — which was the point of
 * putting the contract inside the use case.</p>
 *
 * <p>Degradation is deliberate and layered. A forecast covering a single hour cannot
 * distinguish times, so it is reported as a trip-level context and weather contributes
 * nothing; anything unusable becomes an unavailable context. Any failure, including an
 * implementation that throws where the port says it should not, also becomes unavailable.
 * Weather is a preference, and losing the forecast must never cost the traveller their
 * schedule.</p>
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
            final List<WeatherWarning> hourly = weatherService.getHourlyWarnings(trip);
            if (hourly == null || hourly.isEmpty()) {
                return WeatherContext.unavailable();
            }

            final Map<Integer, WeatherSeverity> byHour = new HashMap<>();
            for (WeatherWarning warning : hourly) {
                if (warning == null || warning.getTime() == null
                        || warning.getSeverity() == null) {
                    continue;
                }
                // Later readings for the same hour win; providers occasionally repeat one.
                byHour.put(warning.getTime().getHour(), warning.getSeverity());
            }

            if (byHour.isEmpty()) {
                return WeatherContext.unavailable();
            }
            if (byHour.size() == 1) {
                // One known hour says what the weather is, not when to do things. Reporting
                // it as trip-level keeps canDistinguishTimes() false, so the preference is
                // withheld rather than offered as a choice that would change nothing.
                return WeatherContext.tripLevel(byHour.values().iterator().next());
            }
            return WeatherContext.hourly(byHour);
        }
        catch (RuntimeException exception) {
            return WeatherContext.unavailable();
        }
    }
}
