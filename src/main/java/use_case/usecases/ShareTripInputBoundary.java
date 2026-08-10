package use_case.usecases;

/** Application boundary for producing a portable, human-readable itinerary. */
public interface ShareTripInputBoundary {
    /**
     * Performs the e xe cu te operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    String execute(String tripId);
}
