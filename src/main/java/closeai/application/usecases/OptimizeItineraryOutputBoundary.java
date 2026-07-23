package closeai.application.usecases;

/** Presents successful or failed current-itinerary compaction. */
public interface OptimizeItineraryOutputBoundary {
    void presentSuccess(OptimizeItineraryOutputData outputData);

    void presentFailure(String errorMessage);
}
