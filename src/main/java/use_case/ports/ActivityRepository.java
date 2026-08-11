package use_case.ports;

import java.util.List;
import java.util.Optional;

import entity.entities.Activity;

public interface ActivityRepository {
    /**
     * Returns every activity currently stored.
     *
     * @return every stored activity, in no guaranteed order
     */
    List<Activity> findAll();

    /**
     * Looks up an activity by its identifier.
     *
     * @param id the identifier to look for
     * @return the activity, or empty when no such activity is stored
     */
    Optional<Activity> findById(String id);
}
