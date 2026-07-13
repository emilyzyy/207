package closeai.infrastructure.persistence;

import closeai.application.ports.TripRepository;
import closeai.domain.entities.Trip;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTripRepository implements TripRepository {
    private final Map<String, Trip> trips = new ConcurrentHashMap<String, Trip>();
    public Trip save(Trip trip) { trips.put(trip.getId(), trip); return trip; }
    public Optional<Trip> findById(String id) { return Optional.ofNullable(trips.get(id)); }
}
