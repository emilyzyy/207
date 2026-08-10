package use_case.autoschedule;

import java.util.Collections;
import java.util.List;

/**
 * A request to change the unsaved proposal that is currently on screen.
 *
 * <p>Carries the proposal itself rather than an identifier for one, because there is no stored
 * proposal to identify: a Preview lives entirely in the view model until Apply. Handing the
 * rows back in keeps the use case free of per-session state while still letting the edit be
 * decided in the application layer rather than in Swing.</p>
 */
public final class ProposalEditInputData {

    private final String tripId;
    private final List<ProposedEventData> proposedRows;
    private final String removeEventId;
    private final entity.valueobjects.TransportationMode mode;
    private final String previewFingerprint;

    public ProposalEditInputData(String tripId, List<ProposedEventData> proposedRows,
                                 String removeEventId,
                                 entity.valueobjects.TransportationMode mode,
                                 String previewFingerprint) {
        this.tripId = tripId == null ? "" : tripId;
        this.proposedRows = Collections.unmodifiableList(new java.util.ArrayList<>(
                proposedRows == null ? Collections.<ProposedEventData>emptyList() : proposedRows));
        this.removeEventId = removeEventId == null ? "" : removeEventId;
        this.mode = mode;
        this.previewFingerprint = previewFingerprint == null ? "" : previewFingerprint;
    }

    public String getTripId() {
        return tripId;
    }

    /** The proposal as it currently stands, including any earlier draft edits. */
    public List<ProposedEventData> getProposedRows() {
        return proposedRows;
    }

    public String getRemoveEventId() {
        return removeEventId;
    }

    public entity.valueobjects.TransportationMode getMode() {
        return mode;
    }

    /** Carried through untouched so Apply can still detect a Day Plan that moved on. */
    public String getPreviewFingerprint() {
        return previewFingerprint;
    }
}
