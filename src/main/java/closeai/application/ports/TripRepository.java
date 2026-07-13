package closeai.application.ports;

import closeai.domain.entities.Trip;
import java.util.Optional;

public interface TripRepository {
    Trip save(Trip trip);
    Optional<Trip> findById(String id);
}
