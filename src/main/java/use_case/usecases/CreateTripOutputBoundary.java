package use_case.usecases;

/** Presents successful or failed new-trip creation. */
public interface CreateTripOutputBoundary {
    /**
     * Presents a successfully created trip.
     *
     * @param outputData the created trip and a confirmation message
     */
    void presentSuccess(CreateTripOutputData outputData);

    /**
     * Presents a creation failure.
     *
     * @param errorMessage the reason creation failed
     */
    void presentFailure(String errorMessage);
}
