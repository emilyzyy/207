package use_case.usecases;

import entity.entities.Activity;
import entity.entities.Trip;
import use_case.ports.ActivityRepository;
import use_case.ports.TripRepository;

public final class BookmarkActivityUseCase {
    private final TripRepository trips;
    private final ActivityRepository activities;

    public BookmarkActivityUseCase(TripRepository trips, ActivityRepository activities) {
        this.trips = trips;
        this.activities = activities;
    }

    /**
     * Performs the e xe cu te operation.
     * @param activityId the a ct iv it yi d value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public Trip execute(String tripId, String activityId) {
        final Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        final Activity activity = activities.findById(activityId).orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        trip.bookmark(activity);
        return trips.save(trip);
    }
}
