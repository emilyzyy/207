package use_case.ports;

import java.util.List;
import java.util.Optional;

import entity.entities.Trip;

public interface TripRepository {
    /**
     * Stores the trip, replacing any existing entry with the same identifier.
     *
     * @param trip the trip to store
     * @return the stored trip
     */
    Trip save(Trip trip);

    /**
     * Looks up a trip by its identifier.
     *
     * @param id the identifier to look for
     * @return the trip, or empty when no such trip is stored
     */
    Optional<Trip> findById(String id);

    /**
     * Returns every trip currently stored.
     *
     * @return every stored trip, in no guaranteed order
     */
    List<Trip> findAll();

    /**
     * Removes one complete trip aggregate.
     * @param id the i d value
     * @return the result of the operation
     */
    default boolean deleteById(String id) {
        return false;
    }
}
