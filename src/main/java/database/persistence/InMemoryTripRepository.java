package database.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import entity.entities.Trip;
import use_case.ports.TripRepository;

public final class InMemoryTripRepository implements TripRepository {
    private final Map<String, Trip> trips = new ConcurrentHashMap<String, Trip>();

    /**
     * Stores the trip, replacing any existing entry with the same identifier.
     *
     * @param trip the trip to store
     * @return the stored trip
     */
    public Trip save(Trip trip) {
        trips.put(trip.getId(), trip);
        return trip;
    }

    /**
     * Looks up a trip by its identifier.
     *
     * @param id the identifier to look for
     * @return the trip, or empty when no such trip is stored
     */
    public Optional<Trip> findById(String id) {
        return Optional.ofNullable(trips.get(id));
    }

    /**
     * Returns every trip currently stored.
     *
     * @return every stored trip, in no guaranteed order
     */
    public List<Trip> findAll() {
        return new ArrayList<Trip>(trips.values());
    }

    /**
     * Removes the trip with the given identifier.
     *
     * @param id the identifier to remove
     * @return whether a stored entry was actually removed
     */
    public boolean deleteById(String id) {
        return id != null && trips.remove(id) != null;
    }
}
