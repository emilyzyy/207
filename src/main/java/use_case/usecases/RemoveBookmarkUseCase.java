package use_case.usecases;

import entity.entities.Trip;
import use_case.ports.TripRepository;

public final class RemoveBookmarkUseCase {
    private final TripRepository trips;

    public RemoveBookmarkUseCase(TripRepository trips) {
        this.trips = trips;
    }

    /**
     * Performs the e xe cu te operation.
     * @param activityId the a ct iv it yi d value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public Trip execute(String tripId, String activityId) {
        final Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        trip.removeBookmark(activityId);
        return trips.save(trip);
    }
}
