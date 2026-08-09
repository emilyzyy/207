package use_case.autoschedule;

import entity.valueobjects.WeatherOption;

import entity.valueobjects.TransportationMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the user asked for when generating a Preview.
 *
 * <p>Carries identifiers and plain values rather than a Trip, so nothing mutable
 * crosses into the use case and the caller cannot hand the Interactor an entity it
 * might change by accident.</p>
 *
 * <p>There are two scheduling preferences rather than a panel of them. Minimising travel,
 * cutting wasted waiting, sensible meal times and daylight for outdoor activities are what
 * the feature is for and are always applied. Whether to keep the order the traveller
 * already arranged is a genuine matter of taste, and whether to consider weather is a
 * question the traveller can only be asked when the forecast can actually distinguish one
 * time of day from another — see {@link WeatherOption}.</p>
 */
public final class AutoScheduleInputData {

    private final String tripId;
    private final LocalTime availableStart;
    private final LocalTime availableEnd;
    private final TransportationMode transportationMode;
    private final Set<String> lockedEventIds;
    private final List<TimeWindow> unavailableWindows;
    private final boolean keepCurrentOrder;
    private final boolean considerWeather;
    private final boolean minimizeTravel;
    private final boolean minimizeGaps;
    private final boolean preserveMealtimes;
    private final boolean preferDaylight;

    public AutoScheduleInputData(String tripId, LocalTime availableStart, LocalTime availableEnd,
                                 TransportationMode transportationMode,
                                 Set<String> lockedEventIds,
                                 List<TimeWindow> unavailableWindows,
                                 boolean keepCurrentOrder,
                                 boolean considerWeather) {
        this(tripId, availableStart, availableEnd, transportationMode, lockedEventIds,
                unavailableWindows, keepCurrentOrder, considerWeather, true, true, true, true);
    }

    /**
     * @param minimizeTravel    whether shorter journeys make one day better than another
     * @param minimizeGaps      whether avoidable waiting counts against a day
     * @param preserveMealtimes whether meals prefer a customary window
     * @param preferDaylight    whether outdoor activities prefer daylight
     */
    public AutoScheduleInputData(String tripId, LocalTime availableStart, LocalTime availableEnd,
                                 TransportationMode transportationMode,
                                 Set<String> lockedEventIds,
                                 List<TimeWindow> unavailableWindows,
                                 boolean keepCurrentOrder,
                                 boolean considerWeather,
                                 boolean minimizeTravel, boolean minimizeGaps,
                                 boolean preserveMealtimes, boolean preferDaylight) {
        this.minimizeTravel = minimizeTravel;
        this.minimizeGaps = minimizeGaps;
        this.preserveMealtimes = preserveMealtimes;
        this.preferDaylight = preferDaylight;
        this.tripId = tripId == null ? "" : tripId.trim();
        this.availableStart = availableStart;
        this.availableEnd = availableEnd;
        this.transportationMode = transportationMode;
        this.lockedEventIds = Collections.unmodifiableSet(new LinkedHashSet<>(
                lockedEventIds == null ? Collections.<String>emptySet() : lockedEventIds));
        this.unavailableWindows = Collections.unmodifiableList(new ArrayList<>(
                unavailableWindows == null ? Collections.<TimeWindow>emptyList() : unavailableWindows));
        this.keepCurrentOrder = keepCurrentOrder;
        this.considerWeather = considerWeather;
    }

    public String getTripId() {
        return tripId;
    }

    public LocalTime getAvailableStart() {
        return availableStart;
    }

    public LocalTime getAvailableEnd() {
        return availableEnd;
    }

    public TransportationMode getTransportationMode() {
        return transportationMode;
    }

    public Set<String> getLockedEventIds() {
        return lockedEventIds;
    }

    public List<TimeWindow> getUnavailableWindows() {
        return unavailableWindows;
    }

    /** Leave my activities in the order I put them, if possible. */
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
     * Whether the traveller asked for weather to be taken into account.
     *
     * <p>Only meaningful when the forecast could distinguish times in the first place; the
     * use case checks that again rather than trusting the dialog, so a stale or mistaken
     * tick costs nothing but the tick.</p>
     */
    public boolean isConsiderWeather() {
        return considerWeather;
    }
}
