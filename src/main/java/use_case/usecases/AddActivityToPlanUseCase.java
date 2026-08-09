package use_case.usecases;

import use_case.ports.ActivityRepository;
import use_case.ports.TripRepository;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.EventType;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AddActivityToPlanUseCase {
    private final TripRepository trips;
    private final ActivityRepository activities;
    public AddActivityToPlanUseCase(TripRepository trips, ActivityRepository activities) {
        this.trips = trips; this.activities = activities;
    }
    public Trip execute(String tripId, String activityId, LocalTime preferredStart) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        Activity activity = activities.findById(activityId).orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        LocalTime start = preferredStart == null ? nextAvailableTime(trip) : preferredStart;
        return add(trip, activity, start,
                start.plusMinutes(activity.getEstimatedDurationMinutes()));
    }

    public Trip execute(String tripId, String activityId, LocalTime start, LocalTime end) {
        Trip trip = trips.findById(tripId).orElseThrow(
                () -> new IllegalArgumentException("Trip not found"));
        Activity activity = activities.findById(activityId).orElseThrow(
                () -> new IllegalArgumentException("Activity not found"));
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must follow start time");
        }
        return add(trip, activity, start, end);
    }

    private Trip add(Trip trip, Activity activity, LocalTime start, LocalTime end) {
        List<ScheduledEvent> updated = new ArrayList<>(trip.getScheduledEvents());
        updated.add(new ScheduledEvent(UUID.randomUUID().toString(), activity, start, end,
                EventType.ACTIVITY, "Added manually"));
        updated.sort(Comparator.comparing(ScheduledEvent::getStartTime));
        return trips.save(trip.copyWithSchedule(updated));
    }
    private LocalTime nextAvailableTime(Trip trip) {
        if (trip.getScheduledEvents().isEmpty()) return trip.getStartTime();
        return trip.getScheduledEvents().get(trip.getScheduledEvents().size() - 1).getEndTime().plusMinutes(15);
    }
}
