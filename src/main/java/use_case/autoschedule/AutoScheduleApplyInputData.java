package use_case.autoschedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The Preview the user accepted, sent back to be saved. */
public final class AutoScheduleApplyInputData {

    private final String tripId;
    private final String expectedFingerprint;
    private final List<ProposedEventData> proposedEvents;

    public AutoScheduleApplyInputData(String tripId, String expectedFingerprint,
                                      List<ProposedEventData> proposedEvents) {
        this.tripId = tripId == null ? "" : tripId.trim();
        this.expectedFingerprint = expectedFingerprint == null ? "" : expectedFingerprint;
        this.proposedEvents = Collections.unmodifiableList(new ArrayList<>(
                proposedEvents == null
                        ? Collections.<ProposedEventData>emptyList() : proposedEvents));
    }

    public String getTripId() {
        return tripId;
    }

    /** The fingerprint the Preview was built from; Apply refuses if it no longer matches. */
    public String getExpectedFingerprint() {
        return expectedFingerprint;
    }

    public List<ProposedEventData> getProposedEvents() {
        return proposedEvents;
    }
}
