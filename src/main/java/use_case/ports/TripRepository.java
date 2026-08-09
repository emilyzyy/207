package use_case.ports;

import entity.entities.Trip;
import java.util.List;
import java.util.Optional;

public interface TripRepository {
    Trip save(Trip trip);
    Optional<Trip> findById(String id);
    List<Trip> findAll();

    /** Removes one complete trip aggregate. */
    default boolean deleteById(String id) {
        return false;
    }
}
