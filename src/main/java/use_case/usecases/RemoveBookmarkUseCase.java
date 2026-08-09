package use_case.usecases;

import use_case.ports.TripRepository;
import entity.entities.Trip;

public final class RemoveBookmarkUseCase {
    private final TripRepository trips;
    public RemoveBookmarkUseCase(TripRepository trips) { this.trips = trips; }
    public Trip execute(String tripId, String activityId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        trip.removeBookmark(activityId);
        return trips.save(trip);
    }
}
