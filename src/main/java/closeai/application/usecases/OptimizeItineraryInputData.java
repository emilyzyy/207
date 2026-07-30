package closeai.application.usecases;

/** Immutable input for compacting the activities already in a trip's schedule. */
public final class OptimizeItineraryInputData {
    private final String tripId;

    public OptimizeItineraryInputData(String tripId) {
        this.tripId = tripId;
    }

    public String getTripId() {
        return tripId;
    }
}
