package closeai.application.usecases;

/** Presents successful or failed itinerary PNG share export. */
public interface ShareItineraryOutputBoundary {
    void presentSuccess(ShareItineraryOutputData outputData);

    void presentFailure(String errorMessage);
}
