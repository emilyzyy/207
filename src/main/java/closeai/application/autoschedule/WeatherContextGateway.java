package closeai.application.autoschedule;

import closeai.domain.entities.Trip;

/**
 * Inward-facing forecast contract owned by the Autoschedule use case.
 *
 * <p>The interface expresses what scheduling needs — severity it can attach to a time —
 * rather than the shape of whatever service supplies it. The concrete adapter lives
 * further out and implements this, keeping the dependency pointing inward.</p>
 */
public interface WeatherContextGateway {

    /**
     * Forecast context for the trip's day.
     *
     * <p>Implementations report {@link WeatherContext#unavailable()} rather than throwing
     * when the forecast cannot be obtained: weather is a preference, and losing it must
     * never cost the user their schedule.</p>
     */
    WeatherContext contextFor(Trip trip);
}
