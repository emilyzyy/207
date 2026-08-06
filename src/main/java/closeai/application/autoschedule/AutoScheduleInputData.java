package closeai.application.autoschedule;

import closeai.domain.valueobjects.TransportationMode;
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
 * <p>There is one scheduling preference rather than a panel of them. Sensible travel,
 * meal times, daylight and weather handling are what the feature is for and are always
 * applied; whether to keep the order the traveller already arranged is the one genuine
 * matter of taste.</p>
 */
public final class AutoScheduleInputData {

    private final String tripId;
    private final LocalTime availableStart;
    private final LocalTime availableEnd;
    private final TransportationMode transportationMode;
    private final Set<String> lockedEventIds;
    private final List<TimeWindow> unavailableWindows;
    private final boolean keepCurrentOrder;

    public AutoScheduleInputData(String tripId, LocalTime availableStart, LocalTime availableEnd,
                                 TransportationMode transportationMode,
                                 Set<String> lockedEventIds,
                                 List<TimeWindow> unavailableWindows,
                                 boolean keepCurrentOrder) {
        this.tripId = tripId == null ? "" : tripId.trim();
        this.availableStart = availableStart;
        this.availableEnd = availableEnd;
        this.transportationMode = transportationMode;
        this.lockedEventIds = Collections.unmodifiableSet(new LinkedHashSet<>(
                lockedEventIds == null ? Collections.<String>emptySet() : lockedEventIds));
        this.unavailableWindows = Collections.unmodifiableList(new ArrayList<>(
                unavailableWindows == null ? Collections.<TimeWindow>emptyList() : unavailableWindows));
        this.keepCurrentOrder = keepCurrentOrder;
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

    /** The traveller's one choice: leave my activities in the order I put them, if possible. */
    public boolean isKeepCurrentOrder() {
        return keepCurrentOrder;
    }
}
