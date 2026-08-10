package use_case.tripassistant;

/** Presenter boundary for completed or failed trip-assistant turns. */
public interface TripAssistantOutputBoundary {
    /**
     * Performs the p re se nt su cc es s operation.
     * @param outputData the o ut pu td at a value
     */
    void presentSuccess(TripAssistantOutputData outputData);

    /**
     * Performs the p re se nt fa il ur e operation.
     * @param message the m es sa ge value
     */
    void presentFailure(String message);
}
