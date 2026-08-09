package use_case.ports;

import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;

/** Read boundary dedicated to user-driven activity search. */
public interface ActivitySearchGateway {
    ActivitySearchResult search(ActivitySearchRequest request);
}
