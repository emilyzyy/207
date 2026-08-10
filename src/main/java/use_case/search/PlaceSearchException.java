package use_case.search;

/** Infrastructure failure translated into an application-level search category. */
public final class PlaceSearchException extends RuntimeException {
    private final SearchFailure failure;

    public PlaceSearchException(SearchFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public PlaceSearchException(SearchFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public SearchFailure getFailure() {
        return failure;
    }
}
