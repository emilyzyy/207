package use_case.usecases;

import use_case.ports.TripRepository;

/** Deletes one trip through the repository boundary. */
public final class DeleteTripUseCase {
    private final TripRepository trips;

    public DeleteTripUseCase(TripRepository trips) {
        if (trips == null) throw new IllegalArgumentException("Trip repository is required");
        this.trips = trips;
    }

    public void execute(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            throw new IllegalArgumentException("Trip id is required");
        }
        if (!trips.findById(tripId).isPresent()) {
            throw new IllegalArgumentException("Trip not found: " + tripId);
        }
        if (!trips.deleteById(tripId)) {
            throw new IllegalStateException("Trip could not be deleted");
        }
    }
}
