package use_case.ports;

import java.util.List;
import java.util.Optional;

import entity.entities.Trip;

public interface TripRepository {
    /**
     * Performs the s av e operation.
     * @param trip the t ri p value
     * @return the result of the operation
     */
    Trip save(Trip trip);

    /**
     * Performs the f in db yi d operation.
     * @param id the i d value
     * @return the result of the operation
     */
    Optional<Trip> findById(String id);

    /**
     * Performs the f in da ll operation.
     * @return the result of the operation
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
