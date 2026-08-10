package use_case.ports;

import java.util.List;
import java.util.Optional;

import entity.entities.Activity;

public interface ActivityRepository {
    /**
     * Performs the f in da ll operation.
     * @return the result of the operation
     */
    List<Activity> findAll();

    /**
     * Performs the f in db yi d operation.
     * @param id the i d value
     * @return the result of the operation
     */
    Optional<Activity> findById(String id);
}
