package closeai.application.ports;

import closeai.domain.entities.Trip;
import java.util.Optional;

/**
 * Persistence port for day itineraries ({@link Trip} aggregates).
 * Narrow interface so edit-itinerary depends only on itinerary load/save (ISP).
 */
public interface ItineraryDataAccessInterface {
    Optional<Trip> loadItinerary(String itineraryId);

    Trip saveItinerary(Trip itinerary);

    boolean existsById(String itineraryId);
}
