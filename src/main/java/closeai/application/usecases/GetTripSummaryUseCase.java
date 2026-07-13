package closeai.application.usecases;

import closeai.application.ports.TripRepository;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.EventType;
import java.time.format.DateTimeFormatter;

public final class GetTripSummaryUseCase {
    private final TripRepository trips;
    public GetTripSummaryUseCase(TripRepository trips) { this.trips = trips; }
    public String execute(String tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        StringBuilder summary = new StringBuilder("CloseAI trip to ").append(trip.getDestination())
                .append(" on ").append(trip.getDate()).append("\n");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("h:mm a");
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            summary.append(event.getStartTime().format(time)).append(" — ")
                    .append(event.getEventType() == EventType.TRAVEL ? event.getNotes() : event.getActivity().getName()).append("\n");
        }
        return summary.toString().trim();
    }
}
