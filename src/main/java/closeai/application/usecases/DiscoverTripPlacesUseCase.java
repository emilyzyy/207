package closeai.application.usecases;

import closeai.application.ports.PlacesService;
import closeai.application.ports.PlacesWriter;
import closeai.application.ports.TripRepository;
import closeai.domain.entities.Activity;
import closeai.domain.entities.Trip;
import java.util.List;

/**
 * Records the pool of places discovered for a trip's destination so the trip can be planned
 * against real activities without the outer layers mutating aggregates or repositories.
 */
public final class DiscoverTripPlacesUseCase {
    private final TripRepository trips;
    private final PlacesService places;
    private final PlacesWriter placesWriter;

    public DiscoverTripPlacesUseCase(TripRepository trips, PlacesService places,
                                     PlacesWriter placesWriter) {
        this.trips = trips;
        this.places = places;
        this.placesWriter = placesWriter;
    }

    /** Searches for real places around the destination and records them on the trip. */
    public Trip execute(String tripId, String destination) {
        List<Activity> discovered = places.search(destination, "");
        if (discovered.isEmpty()) {
            return requireTrip(tripId);
        }
        return record(tripId, discovered);
    }

    /** Records an already-known set of places (e.g. mock or cached data) on the trip. */
    public Trip record(String tripId, List<Activity> discovered) {
        Trip trip = requireTrip(tripId);
        if (discovered == null || discovered.isEmpty()) {
            return trip;
        }
        placesWriter.addAll(discovered);
        trip.setDiscoveredPlaces(discovered);
        return trips.save(trip);
    }

    private Trip requireTrip(String tripId) {
        return trips.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));
    }
}
