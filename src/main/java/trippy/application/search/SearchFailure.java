package trippy.application.search;

/** Failure categories the presenter can explain without depending on HTTP details. */
public enum SearchFailure {
    NONE,
    NO_MATCH,
    INVALID_DESTINATION,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE
}
