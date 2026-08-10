package use_case.usecases;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.TripDay;
import entity.valueobjects.EventType;
import use_case.ports.TripRepository;

public final class GetTripSummaryUseCase {
    private final TripRepository trips;
    public GetTripSummaryUseCase(TripRepository trips) { this.trips = trips; }
    public String execute(String tripId) {
        final Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        final DateTimeFormatter time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        final StringBuilder summary = new StringBuilder("Trippy trip to ")
                .append(trip.getDestination()).append("\n")
                .append("Transportation: ")
                .append(trip.getTransportationMode()).append("\n");
        if (trip.getDayCount() <= 1) {
            summary.append("Date: ").append(trip.getDate()).append("\n")
                    .append("Time: ").append(trip.getStartTime())
                    .append(" – ").append(trip.getEndTime()).append("\n\n")
                    .append("Itinerary\n");
            appendEvents(summary, trip.getScheduledEvents(), time);
        } else {
            summary.append("Days: ").append(trip.getDayCount()).append("\n\n");
            for (int i = 0; i < trip.getDayCount(); i++) {
                final TripDay day = trip.getDay(i);
                summary.append("Day ").append(i + 1).append(" · ").append(day.getDate())
                        .append(" (").append(day.getStartTime()).append(" – ")
                        .append(day.getEndTime()).append(")\n");
                appendEvents(summary, day.getScheduledEvents(), time);
                summary.append("\n");
            }
        }
        return summary.append("Shared from Trippy").toString();
    }

    private void appendEvents(StringBuilder summary, java.util.List<ScheduledEvent> events,
                              DateTimeFormatter time) {
        if (events.isEmpty()) {
            summary.append("No activities scheduled yet.\n");
            return;
        }
        for (ScheduledEvent event : events) {
            summary.append(event.getStartTime().format(time))
                    .append(" – ").append(event.getEndTime().format(time))
                    .append(" · ").append(eventName(event)).append("\n");
        }
    }

    private String eventName(ScheduledEvent event) {
        if (event.getEventType() == EventType.TRAVEL) {
            return event.getNotes().trim().isEmpty() ? "Travel" : event.getNotes();
        }
        return event.getActivity() == null
                ? event.getEventType().toString() : event.getActivity().getName();
    }
}
