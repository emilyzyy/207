package use_case.usecases;

import entity.entities.Trip;

/** Successful output from creating a new trip. */
public final class CreateTripOutputData {
    private final Trip trip;
    private final String message;

    /**
     * Creates output for a newly created trip.
     *
     * @param trip the persisted trip
     * @param message a confirmation message
     * @throws IllegalArgumentException if trip is null
     */
    public CreateTripOutputData(Trip trip, String message) {
        if (trip == null) {
            throw new IllegalArgumentException("Created trip is required");
        }
        this.trip = trip;
        if (message == null) {
            this.message = "";
        }
        else {
            this.message = message;
        }
    }

    /**
     * Returns the created trip.
     *
     * @return the persisted trip
     */
    public Trip getTrip() {
        return this.trip;
    }

    /**
     * Returns the confirmation message.
     *
     * @return the message
     */
    public String getMessage() {
        return this.message;
    }
}
