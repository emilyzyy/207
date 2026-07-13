package closeai.application.usecases;

import closeai.application.ports.TripRepository;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import java.time.LocalTime;

public final class EditScheduledEventUseCase {
    private final TripRepository trips;
    public EditScheduledEventUseCase(TripRepository trips) { this.trips = trips; }
    public Trip execute(String tripId, String eventId, LocalTime start, LocalTime end, String notes) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        ScheduledEvent event = trip.findEvent(eventId);
        if (event == null) throw new IllegalArgumentException("Event not found");
        if (start.isBefore(trip.getStartTime()) || end.isAfter(trip.getEndTime()))
            throw new IllegalArgumentException("Event must stay inside the trip window");
        event.reschedule(start, end, notes);
        return trips.save(trip);
    }
}
