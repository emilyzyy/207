package use_case.usecases;

import use_case.ports.TripRepository;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.EventType;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class GetTripSummaryUseCase {
    private final TripRepository trips;
    public GetTripSummaryUseCase(TripRepository trips) { this.trips = trips; }
    public String execute(String tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        StringBuilder summary = new StringBuilder("Trippy trip to ")
                .append(trip.getDestination()).append("\n")
                .append("Date: ").append(trip.getDate()).append("\n")
                .append("Time: ").append(trip.getStartTime())
                .append(" – ").append(trip.getEndTime()).append("\n")
                .append("Transportation: ")
                .append(trip.getTransportationMode()).append("\n\n")
                .append("Itinerary\n");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
        if (trip.getScheduledEvents().isEmpty()) {
            summary.append("No activities scheduled yet.\n");
        } else {
            for (ScheduledEvent event : trip.getScheduledEvents()) {
                summary.append(event.getStartTime().format(time))
                        .append(" – ").append(event.getEndTime().format(time))
                        .append(" · ").append(eventName(event)).append("\n");
            }
        }
        return summary.append("\nShared from Trippy").toString();
    }

    private String eventName(ScheduledEvent event) {
        if (event.getEventType() == EventType.TRAVEL) {
            return event.getNotes().trim().isEmpty() ? "Travel" : event.getNotes();
        }
        return event.getActivity() == null
                ? event.getEventType().toString() : event.getActivity().getName();
    }
}
