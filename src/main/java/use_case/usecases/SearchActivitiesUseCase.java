package use_case.usecases;

import use_case.ports.ActivitySearchGateway;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;

/** Executes user-driven activity discovery through its dedicated application boundary. */
public final class SearchActivitiesUseCase {
    private final ActivitySearchGateway searchGateway;

    public SearchActivitiesUseCase(ActivitySearchGateway searchGateway) {
        if (searchGateway == null) {
            throw new IllegalArgumentException("Activity search gateway is required");
        }
        this.searchGateway = searchGateway;
    }

    public ActivitySearchResult execute(ActivitySearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Activity search request is required");
        }
        return searchGateway.search(request);
    }
}
