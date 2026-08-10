package use_case.usecases;

import java.util.List;

import entity.entities.Activity;
import use_case.ports.PlacesService;
import use_case.ports.ViewportPlacesLoader;

/** Loads places inside a map viewport for the given trip destination. */
public final class LoadViewportPlacesUseCase implements ViewportPlacesLoader {
    private final PlacesService places;
    private final String destination;

    /**
     * Creates a viewport loader bound to a destination.
     *
     * @param places the places service backing the map
     * @param destination the trip destination whose viewport is being loaded
     * @throws IllegalArgumentException if the places service is null
     */
    public LoadViewportPlacesUseCase(PlacesService places, String destination) {
        if (places == null) {
            throw new IllegalArgumentException("Places service is required");
        }
        this.places = places;
        this.destination = destination;
    }

    @Override
    public List<Activity> load(double south, double west, double north, double east,
                               int maxResults) {
        return this.places.searchInBounds(this.destination, south, west, north, east, maxResults);
    }
}
