package use_case.ports;

import java.util.Optional;

import entity.entities.Trip;

/**
 * Persistence port for day itineraries ({@link Trip} aggregates).
 * Narrow interface so edit-itinerary depends only on itinerary load/save (ISP).
 */
public interface ItineraryDataAccessInterface {
    /**
     * Performs the l oa di ti ne ra ry operation.
     * @param itineraryId the i ti ne ra ry id value
     * @return the result of the operation
     */
    Optional<Trip> loadItinerary(String itineraryId);

    /**
     * Performs the s av ei ti ne ra ry operation.
     * @param itinerary the i ti ne ra ry value
     * @return the result of the operation
     */
    Trip saveItinerary(Trip itinerary);

    /**
     * Performs the e xi st sb yi d operation.
     * @param itineraryId the i ti ne ra ry id value
     * @return the result of the operation
     */
    boolean existsById(String itineraryId);
}
