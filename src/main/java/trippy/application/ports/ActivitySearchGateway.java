package trippy.application.ports;

import trippy.application.search.ActivitySearchRequest;
import trippy.application.search.ActivitySearchResult;

/** Read boundary dedicated to user-driven activity search. */
public interface ActivitySearchGateway {
    ActivitySearchResult search(ActivitySearchRequest request);
}
