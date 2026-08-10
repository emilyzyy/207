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
     * Performs the s av e operation.
     * @param trip the t ri p value
     * @return the result of the operation
     */
    public Trip save(Trip trip) {
        trips.put(trip.getId(), trip);
        return trip;
    }

    /**
     * Performs the f in db yi d operation.
     * @param id the i d value
     * @return the result of the operation
     */
    public Optional<Trip> findById(String id) {
        return Optional.ofNullable(trips.get(id));
    }

    /**
     * Performs the f in da ll operation.
     * @return the result of the operation
     */
    public List<Trip> findAll() {
        return new ArrayList<Trip>(trips.values());
    }

    /**
     * Performs the d el et eb yi d operation.
     * @param id the i d value
     * @return the result of the operation
     */
    public boolean deleteById(String id) {
        return id != null && trips.remove(id) != null;
    }
}
