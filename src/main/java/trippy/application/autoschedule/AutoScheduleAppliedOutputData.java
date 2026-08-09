package trippy.application.autoschedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Confirmation that the proposed schedule is now the itinerary. */
public final class AutoScheduleAppliedOutputData {

    private final String tripId;
    private final List<ProposedEventData> savedEvents;
    private final String fingerprint;

    public AutoScheduleAppliedOutputData(String tripId, List<ProposedEventData> savedEvents,
                                         String fingerprint) {
        this.tripId = tripId == null ? "" : tripId;
        this.savedEvents = Collections.unmodifiableList(new ArrayList<>(
                savedEvents == null ? Collections.<ProposedEventData>emptyList() : savedEvents));
        this.fingerprint = fingerprint == null ? "" : fingerprint;
    }

    public String getTripId() {
        return tripId;
    }

    public List<ProposedEventData> getSavedEvents() {
        return savedEvents;
    }

    public String getFingerprint() {
        return fingerprint;
    }
}
