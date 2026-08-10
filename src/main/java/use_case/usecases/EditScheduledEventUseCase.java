package use_case.usecases;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import use_case.ports.TripRepository;

public final class EditScheduledEventUseCase {
    private final TripRepository trips;

    public EditScheduledEventUseCase(TripRepository trips) {
        this.trips = trips;
    }

    /**
     * Performs the e xe cu te operation.
     * @param start the s ta rt value
     * @param end the e nd value
     * @param notes the n ot es value
     * @param eventId the e ve nt id value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public Trip execute(String tripId, String eventId, LocalTime start, LocalTime end, String notes) {
        final Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        final ScheduledEvent event = trip.findEvent(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Event not found");
        }
        // Retiming an activity invalidates the journeys either side of it, so the derived
        // travel goes rather than being left pointing at the old times.
        final List<ScheduledEvent> updated = new ArrayList<>();
        for (ScheduledEvent existing : ScheduleEdits.withoutDerivedTravel(
                trip.getScheduledEvents())) {
            updated.add(existing.getId().equals(eventId)
                    ? new ScheduledEvent(existing.getId(), existing.getActivity(), start, end,
                            existing.getEventType(), notes)
                    : existing);
        }
        updated.sort(Comparator.comparing(ScheduledEvent::getStartTime));
        return trips.save(trip.copyWithSchedule(updated));
    }
}
