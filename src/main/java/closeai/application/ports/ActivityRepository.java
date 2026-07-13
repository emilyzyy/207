package closeai.application.ports;

import closeai.domain.entities.Activity;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository {
    List<Activity> findAll();
    Optional<Activity> findById(String id);
}
