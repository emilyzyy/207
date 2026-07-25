package closeai.application.usecases;

import closeai.domain.entities.Trip;

/** Successful output from the create-or-edit trip setup workflow. */
public final class TripSetupOutputData {
    private final Trip trip;
    private final boolean created;

    public TripSetupOutputData(Trip trip, boolean created) {
        if (trip == null) {
            throw new IllegalArgumentException("Trip setup output requires a trip");
        }
        this.trip = trip;
        this.created = created;
    }

    public Trip getTrip() {
        return trip;
    }

    public boolean isCreated() {
        return created;
    }
}
