package closeai.application.usecases;

/** Immutable input for sharing an existing itinerary as a PNG. */
public final class ShareItineraryInputData {
    private final String tripId;

    public ShareItineraryInputData(String tripId) {
        this.tripId = tripId;
    }

    public String getTripId() {
        return tripId;
    }
}
