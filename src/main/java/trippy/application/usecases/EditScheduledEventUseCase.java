package trippy.application.usecases;

import trippy.application.ports.TripRepository;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.Trip;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EditScheduledEventUseCase {
    private final TripRepository trips;
    public EditScheduledEventUseCase(TripRepository trips) { this.trips = trips; }
    public Trip execute(String tripId, String eventId, LocalTime start, LocalTime end, String notes) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        ScheduledEvent event = trip.findEvent(eventId);
        if (event == null) throw new IllegalArgumentException("Event not found");
        List<ScheduledEvent> updated = new ArrayList<>();
        for (ScheduledEvent existing : trip.getScheduledEvents()) {
            updated.add(existing.getId().equals(eventId)
                    ? new ScheduledEvent(existing.getId(), existing.getActivity(), start, end,
                            existing.getEventType(), notes)
                    : existing);
        }
        updated.sort(Comparator.comparing(ScheduledEvent::getStartTime));
        return trips.save(trip.copyWithSchedule(updated));
    }
}
