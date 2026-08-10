package use_case.ports;

import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;

/** Read boundary dedicated to user-driven activity search. */
public interface ActivitySearchGateway {
    /**
     * Performs the s ea rc h operation.
     * @param request the r eq ue st value
     * @return the result of the operation
     */
    ActivitySearchResult search(ActivitySearchRequest request);
}
