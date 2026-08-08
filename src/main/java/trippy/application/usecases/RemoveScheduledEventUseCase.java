package trippy.application.usecases;

import trippy.application.ports.TripRepository;
import trippy.domain.entities.Trip;
import trippy.domain.entities.ScheduledEvent;
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
        List<ScheduledEvent> updated = new ArrayList<>(trip.getScheduledEvents());
        updated.removeIf(event -> event.getId().equals(eventId));
        return trips.save(trip.copyWithSchedule(updated));
    }
}
