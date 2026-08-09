package use_case.usecases;

import use_case.ports.PlacesService;
import use_case.ports.ActivitySearchGateway;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;
import entity.entities.Activity;
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
