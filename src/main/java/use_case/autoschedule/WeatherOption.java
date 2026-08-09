package use_case.autoschedule;

/**
 * Whether the weather preference can honestly be offered for a given trip.
 *
 * <p>Weather is the one soft objective the traveller decides about, and the reason is a
 * product one rather than a matter of taste. A forecast is only worth consulting if it can
 * tell 10 a.m. from 3 p.m.; for a trip far enough ahead, providers return a single
 * whole-day outlook, which scores every candidate time alike and so cannot say
 * <em>when</em> to do anything. Offering a checkbox that silently does nothing would be
 * worse than not offering it, so the option is gated on the forecast's actual capability
 * and the reason is shown in words when it is withheld.</p>
 *
 * <p>The capability is asked of the provider rather than inferred from a date cutoff. A
 * cutoff would be a guess about someone else's service that goes stale the moment the
 * provider changes its horizon; asking what the forecast can actually distinguish stays
 * true either way.</p>
 *
 * <p>This is a plain value carried outward to the settings dialog. The UI reads it and
 * renders a checkbox; it never learns which service answered.</p>
 */
public final class WeatherOption {

    /** Shown when a forecast exists but covers the whole day rather than each hour. */
    public static final String NO_HOURLY_FORECAST =
            "Hourly weather is not available for this trip date.";

    /** Shown when no forecast could be obtained at all. */
    public static final String NO_FORECAST =
            "Weather information is not available for this trip date.";

    private static final WeatherOption AVAILABLE = new WeatherOption(true, "");

    private final boolean available;
    private final String unavailableReason;

    private WeatherOption(boolean available, String unavailableReason) {
        this.available = available;
        this.unavailableReason = unavailableReason == null ? "" : unavailableReason;
    }

    /** The forecast can distinguish times, so the traveller may choose to use it. */
    public static WeatherOption available() {
        return AVAILABLE;
    }

    /** The option cannot be offered, with the short sentence explaining why. */
    public static WeatherOption unavailable(String reason) {
        return new WeatherOption(false, reason);
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Whether the checkbox starts ticked. Weather improves a day when it can be applied at
     * all, so it defaults on wherever it is offered; the traveller can still turn it off.
     */
    public boolean isSelectedByDefault() {
        return available;
    }

    /** Empty when the option is available; otherwise a sentence to show beside it. */
    public String getUnavailableReason() {
        return unavailableReason;
    }
}
