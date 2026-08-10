package use_case.usecases;

import java.util.List;

import entity.entities.Activity;
import entity.entities.Trip;
import use_case.ports.PlacesService;
import use_case.ports.PlacesWriter;
import use_case.ports.TripRepository;

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

    /**
     * Searches for real places around the destination and records them on the trip.
     * @param destination the d es ti na ti on value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public Trip execute(String tripId, String destination) {
        final List<Activity> discovered = places.search(destination, "");
        if (discovered.isEmpty()) {
            return requireTrip(tripId);
        }
        return record(tripId, discovered);
    }

    /**
     * Records an already-known set of places (e.g. mock or cached data) on the trip.
     * @param discovered the d is co ve re d value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public Trip record(String tripId, List<Activity> discovered) {
        final Trip trip = requireTrip(tripId);
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
