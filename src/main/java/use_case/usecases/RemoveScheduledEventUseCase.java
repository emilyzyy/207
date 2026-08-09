package use_case.usecases;

import use_case.ports.TripRepository;
import entity.entities.Trip;
import entity.entities.ScheduledEvent;
import java.util.ArrayList;
import java.util.List;

public final class RemoveScheduledEventUseCase {
    private final TripRepository trips;
    public RemoveScheduledEventUseCase(TripRepository trips) { this.trips = trips; }
    public Trip execute(String tripId, String eventId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        if (trip.findEvent(eventId) == null) {
            throw new IllegalArgumentException("Event not found");
        }
        // Drop the generated travel with it: those blocks describe journeys between a
        // particular set of activities, and removing one makes the journeys to and from it
        // meaningless. Autoschedule recomputes them on its next run.
        List<ScheduledEvent> updated = new ArrayList<>(
                ScheduleEdits.withoutDerivedTravel(trip.getScheduledEvents()));
        updated.removeIf(event -> event.getId().equals(eventId));
        return trips.save(trip.copyWithSchedule(updated));
    }
}
