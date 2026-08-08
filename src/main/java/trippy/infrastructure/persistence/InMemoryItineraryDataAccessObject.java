package trippy.infrastructure.persistence;

import trippy.application.ports.ItineraryDataAccessInterface;
import trippy.application.ports.TripRepository;
import trippy.domain.entities.Trip;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public List<Trip> findAll() {
        return new ArrayList<Trip>(itineraries.values());
    }

    @Override
    public boolean deleteById(String id) {
        return id != null && itineraries.remove(id) != null;
    }

    /** Drops all local itineraries (used on sign-out). */
    public void clear() {
        itineraries.clear();
    }
}
