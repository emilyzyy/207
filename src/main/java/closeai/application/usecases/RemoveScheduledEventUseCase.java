package closeai.application.usecases;

import closeai.application.ports.TripRepository;
import closeai.domain.entities.Trip;

public final class RemoveScheduledEventUseCase {
    private final TripRepository trips;
    public RemoveScheduledEventUseCase(TripRepository trips) { this.trips = trips; }
    public Trip execute(String tripId, String eventId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        trip.removeEvent(eventId);
        return trips.save(trip);
    }
}
