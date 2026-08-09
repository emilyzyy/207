package use_case.autoschedule;

import entity.valueobjects.WeatherOption;

import entity.entities.Trip;

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

    /**
     * Whether the weather preference can be offered for this trip.
     *
     * <p>Derived from what the provider actually returns rather than from a date cutoff:
     * a forecast that cannot tell one hour from another cannot inform timing, whoever
     * supplied it and however far away the trip is. An implementation backed by a service
     * that knows its own horizon may answer more cheaply by overriding this, but it must
     * not answer more generously than {@link #contextFor} would.</p>
     */
    default WeatherOption optionFor(Trip trip) {
        WeatherContext context;
        try {
            context = contextFor(trip);
        } catch (RuntimeException exception) {
            // The contract says failures come back as an unavailable context, but asking
            // what is possible must not be able to throw at a caller who only wants to
            // draw a checkbox.
            return WeatherOption.unavailable(WeatherOption.NO_FORECAST);
        }
        if (context == null || !context.isAvailable()) {
            return WeatherOption.unavailable(WeatherOption.NO_FORECAST);
        }
        if (!context.canDistinguishTimes()) {
            return WeatherOption.unavailable(WeatherOption.NO_HOURLY_FORECAST);
        }
        return WeatherOption.available();
    }
}
