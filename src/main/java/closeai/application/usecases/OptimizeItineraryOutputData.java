package closeai.application.usecases;

import closeai.domain.entities.Trip;

/** Successful output from compacting the current itinerary. */
public final class OptimizeItineraryOutputData {
    private final Trip trip;
    private final String message;

    public OptimizeItineraryOutputData(Trip trip, String message) {
        if (trip == null) {
            throw new IllegalArgumentException("Optimized trip is required");
        }
        this.trip = trip;
        this.message = message == null ? "" : message;
    }

    public Trip getTrip() {
        return trip;
    }

    public String getMessage() {
        return message;
    }
}
