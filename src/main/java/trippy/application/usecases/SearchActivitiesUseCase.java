package trippy.application.usecases;

import trippy.application.ports.PlacesService;
import trippy.application.ports.ActivitySearchGateway;
import trippy.application.search.ActivitySearchRequest;
import trippy.application.search.ActivitySearchResult;
import trippy.application.search.SearchFailure;
import trippy.application.search.SearchSource;
import trippy.domain.entities.Activity;
import java.util.List;

public final class SearchActivitiesUseCase {
    private final PlacesService places;
    public SearchActivitiesUseCase(PlacesService places) { this.places = places; }
    public List<Activity> execute(String destination, String query) { return places.search(destination, query); }

    public ActivitySearchResult execute(ActivitySearchRequest request) {
        if (places instanceof ActivitySearchGateway) {
            return ((ActivitySearchGateway) places).search(request);
        }
        List<Activity> activities = places.search(request.getDestination(), request.getQuery());
        return new ActivitySearchResult(activities, SearchSource.LOCAL, false,
                activities.isEmpty() ? SearchFailure.NO_MATCH : SearchFailure.NONE);
    }
}
