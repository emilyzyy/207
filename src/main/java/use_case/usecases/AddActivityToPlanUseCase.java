package use_case.usecases;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.EventType;
import use_case.ports.ActivityRepository;
import use_case.ports.TripRepository;

public final class AddActivityToPlanUseCase {
    private final TripRepository trips;
    private final ActivityRepository activities;

    public AddActivityToPlanUseCase(TripRepository trips, ActivityRepository activities) {
        this.trips = trips;
        this.activities = activities;
    }

    /**
     * Performs the e xe cu te operation.
     * @param activityId the a ct iv it yi d value
     * @param preferredStart the p re fe rr ed st ar t value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public Trip execute(String tripId, String activityId, LocalTime preferredStart) {
        final Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        final Activity activity = activities.findById(activityId).orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        final LocalTime start = preferredStart == null ? nextAvailableTime(trip) : preferredStart;
        return add(trip, activity, start,
                start.plusMinutes(activity.getEstimatedDurationMinutes()));
    }

    /**
     * Performs the e xe cu te operation.
     * @param end the e nd value
     * @param start the s ta rt value
     * @param activityId the a ct iv it yi d value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public Trip execute(String tripId, String activityId, LocalTime start, LocalTime end) {
        final Trip trip = trips.findById(tripId).orElseThrow(
                () -> new IllegalArgumentException("Trip not found"));
        final Activity activity = activities.findById(activityId).orElseThrow(
                () -> new IllegalArgumentException("Activity not found"));
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must follow start time");
        }
        return add(trip, activity, start, end);
    }

    private Trip add(Trip trip, Activity activity, LocalTime start, LocalTime end) {
        // A new activity changes the route, so the previously computed journeys no longer
        // describe this day. They go until Autoschedule works them out again.
        final List<ScheduledEvent> updated = new ArrayList<>(
                ScheduleEdits.withoutDerivedTravel(trip.getScheduledEvents()));
        updated.add(new ScheduledEvent(UUID.randomUUID().toString(), activity, start, end,
                EventType.ACTIVITY, "Added manually"));
        updated.sort(Comparator.comparing(ScheduledEvent::getStartTime));
        return trips.save(trip.copyWithSchedule(updated));
    }

    private LocalTime nextAvailableTime(Trip trip) {
        if (trip.getScheduledEvents().isEmpty()) {
            return trip.getStartTime();
        }
        return trip.getScheduledEvents().get(trip.getScheduledEvents().size() - 1).getEndTime().plusMinutes(15);
    }
}
