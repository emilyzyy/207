package database.persistence;

import use_case.ports.TripRepository;
import entity.entities.Trip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTripRepository implements TripRepository {
    private final Map<String, Trip> trips = new ConcurrentHashMap<String, Trip>();
    public Trip save(Trip trip) { trips.put(trip.getId(), trip); return trip; }
    public Optional<Trip> findById(String id) { return Optional.ofNullable(trips.get(id)); }
    public List<Trip> findAll() { return new ArrayList<Trip>(trips.values()); }
    public boolean deleteById(String id) { return id != null && trips.remove(id) != null; }
}
