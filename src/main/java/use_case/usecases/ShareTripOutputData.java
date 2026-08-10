package use_case.usecases;

import entity.entities.Trip;

/** Framework-free share result: portable text plus the trip used for day-plan cards. */
public final class ShareTripOutputData {
    private final String shareText;
    private final Trip trip;

    public ShareTripOutputData(String shareText, Trip trip) {
        if (shareText == null || shareText.trim().isEmpty()) {
            throw new IllegalArgumentException("Share text is required");
        }
        if (trip == null) {
            throw new IllegalArgumentException("Trip is required");
        }
        this.shareText = shareText;
        this.trip = trip;
    }

    public String getShareText() {
        return shareText;
    }

    public Trip getTrip() {
        return trip;
    }
}
