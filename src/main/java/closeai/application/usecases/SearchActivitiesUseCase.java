package closeai.application.usecases;

import closeai.application.ports.PlacesService;
import closeai.domain.entities.Activity;
import java.util.List;

public final class SearchActivitiesUseCase {
    private final PlacesService places;
    public SearchActivitiesUseCase(PlacesService places) { this.places = places; }
    public List<Activity> execute(String destination, String query) { return places.search(destination, query); }
}
