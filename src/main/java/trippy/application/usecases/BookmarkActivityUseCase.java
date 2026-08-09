package trippy.application.usecases;

import trippy.application.ports.ActivityRepository;
import trippy.application.ports.TripRepository;
import trippy.domain.entities.Activity;
import trippy.domain.entities.Trip;

public final class BookmarkActivityUseCase {
    private final TripRepository trips;
    private final ActivityRepository activities;
    public BookmarkActivityUseCase(TripRepository trips, ActivityRepository activities) {
        this.trips = trips; this.activities = activities;
    }
    public Trip execute(String tripId, String activityId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        Activity activity = activities.findById(activityId).orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        trip.bookmark(activity);
        return trips.save(trip);
    }
}
