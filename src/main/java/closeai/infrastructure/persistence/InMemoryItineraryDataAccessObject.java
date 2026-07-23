package closeai.infrastructure.persistence;

import closeai.application.ports.ItineraryDataAccessInterface;
import closeai.application.ports.TripRepository;
import closeai.domain.entities.Trip;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory itinerary store used by edit-itinerary and as the app's {@link TripRepository}.
 */
public final class InMemoryItineraryDataAccessObject
        implements ItineraryDataAccessInterface, TripRepository {
    private final Map<String, Trip> itineraries = new ConcurrentHashMap<String, Trip>();

    @Override
    public Optional<Trip> loadItinerary(String itineraryId) {
        return findById(itineraryId);
    }

    @Override
    public Trip saveItinerary(Trip itinerary) {
        return save(itinerary);
    }

    @Override
    public boolean existsById(String itineraryId) {
        return itineraryId != null && itineraries.containsKey(itineraryId);
    }

    @Override
    public Trip save(Trip trip) {
        if (trip == null) {
            throw new IllegalArgumentException("Itinerary is required");
        }
        itineraries.put(trip.getId(), trip);
        return trip;
    }

    @Override
    public Optional<Trip> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(itineraries.get(id));
    }
}
