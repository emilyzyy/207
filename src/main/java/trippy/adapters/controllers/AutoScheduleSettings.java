package trippy.adapters.controllers;

import trippy.domain.valueobjects.TransportationMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the settings dialog collected, as plain values.
 *
 * <p>Short by design. Sensible travel, mealtimes and daylight are what the feature is for
 * and are always applied, and travel is always estimated by the fastest of the available
 * modes, so there is no mode to ask about. Two preferences are left to the traveller:
 * whether to keep the order they already arranged, and whether to consider weather — the
 * latter only offered when the forecast can distinguish one time of day from another.</p>
 */
public final class AutoScheduleSettings {

    private final LocalTime availableStart;
    private final LocalTime availableEnd;
    private final List<Window> unavailableWindows;
    private final boolean keepCurrentOrder;
    private final boolean considerWeather;
    private final TransportationMode transportationMode;
    private final boolean minimizeTravel;
    private final boolean minimizeGaps;
    private final boolean preserveMealtimes;
    private final boolean preferDaylight;

    /** Everything the schedule normally weighs, with only the two usual choices given. */
    public AutoScheduleSettings(LocalTime availableStart, LocalTime availableEnd,
                                List<Window> unavailableWindows, boolean keepCurrentOrder,
                                boolean considerWeather) {
        this(availableStart, availableEnd, TransportationMode.FASTEST, unavailableWindows,
                keepCurrentOrder, considerWeather, true, true, true, true);
    }

    public AutoScheduleSettings(LocalTime availableStart, LocalTime availableEnd,
                                TransportationMode transportationMode,
                                List<Window> unavailableWindows, boolean keepCurrentOrder,
                                boolean considerWeather, boolean minimizeTravel,
                                boolean minimizeGaps, boolean preserveMealtimes,
                                boolean preferDaylight) {
        this.transportationMode = transportationMode;
        this.minimizeTravel = minimizeTravel;
        this.minimizeGaps = minimizeGaps;
        this.preserveMealtimes = preserveMealtimes;
        this.preferDaylight = preferDaylight;
        this.availableStart = availableStart;
        this.availableEnd = availableEnd;
        this.unavailableWindows = Collections.unmodifiableList(new ArrayList<>(
                unavailableWindows == null ? Collections.<Window>emptyList() : unavailableWindows));
        this.keepCurrentOrder = keepCurrentOrder;
        this.considerWeather = considerWeather;
    }

    public LocalTime getAvailableStart() {
        return availableStart;
    }

    public LocalTime getAvailableEnd() {
        return availableEnd;
    }

    public List<Window> getUnavailableWindows() {
        return unavailableWindows;
    }

    /** How the traveller is getting around, possibly {@link TransportationMode#FASTEST}. */
    public TransportationMode getTransportationMode() {
        return transportationMode;
    }

    public boolean isMinimizeTravel() {
        return minimizeTravel;
    }

    public boolean isMinimizeGaps() {
        return minimizeGaps;
    }

    public boolean isPreserveMealtimes() {
        return preserveMealtimes;
    }

    public boolean isPreferDaylight() {
        return preferDaylight;
    }

    public boolean isKeepCurrentOrder() {
        return keepCurrentOrder;
    }

    /**
     * Whether the traveller turned on "Avoid bad weather". False whenever the switch was
     * disabled, since a disabled box is never ticked.
     */
    public boolean isConsiderWeather() {
        return considerWeather;
    }

    /** A stretch of the day the traveller is not available for anything. */
    public static final class Window {
        private final LocalTime start;
        private final LocalTime end;

        public Window(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }

        public LocalTime getStart() {
            return start;
        }

        public LocalTime getEnd() {
            return end;
        }
    }
}
