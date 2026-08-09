package app;

import use_case.ports.ItineraryDataAccessInterface;
import use_case.ports.TripRepository;
import entity.entities.Trip;
import java.util.Optional;

/**
 * Application-side adapter so edit-itinerary can reuse {@link TripRepository} storage (DIP).
 */
public final class TripRepositoryItineraryDataAccess implements ItineraryDataAccessInterface {
    private final TripRepository trips;

    public TripRepositoryItineraryDataAccess(TripRepository trips) {
        if (trips == null) {
            throw new IllegalArgumentException("Trip repository is required");
        }
        this.trips = trips;
    }

    @Override
    public Optional<Trip> loadItinerary(String itineraryId) {
        return trips.findById(itineraryId);
    }

    @Override
    public Trip saveItinerary(Trip itinerary) {
        return trips.save(itinerary);
    }

    @Override
    public boolean existsById(String itineraryId) {
        return trips.findById(itineraryId).isPresent();
    }
}
