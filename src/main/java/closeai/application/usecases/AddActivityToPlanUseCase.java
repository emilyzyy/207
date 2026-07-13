package closeai.application.usecases;

import closeai.application.ports.ActivityRepository;
import closeai.application.ports.TripRepository;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.EventType;
import java.time.LocalTime;
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
        LocalTime end = start.plusMinutes(activity.getEstimatedDurationMinutes());
        if (end.isAfter(trip.getEndTime())) throw new IllegalArgumentException("Activity does not fit in the trip window");
        trip.addEvent(new ScheduledEvent(UUID.randomUUID().toString(), activity, start, end,
                EventType.ACTIVITY, "Added manually"));
        return trips.save(trip);
    }
    private LocalTime nextAvailableTime(Trip trip) {
        if (trip.getScheduledEvents().isEmpty()) return trip.getStartTime();
        return trip.getScheduledEvents().get(trip.getScheduledEvents().size() - 1).getEndTime().plusMinutes(15);
    }
}
