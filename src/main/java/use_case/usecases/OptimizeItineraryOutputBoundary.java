package use_case.usecases;

/** Presents successful or failed current-itinerary compaction. */
public interface OptimizeItineraryOutputBoundary {
    /**
     * Performs the p re se nt su cc es s operation.
     * @param outputData the o ut pu td at a value
     */
    void presentSuccess(OptimizeItineraryOutputData outputData);

    /**
     * Performs the p re se nt fa il ur e operation.
     * @param errorMessage the e rr or me ss ag e value
     */
    void presentFailure(String errorMessage);
}
