package closeai.application.tripassistant;

/** Presenter boundary for completed or failed trip-assistant turns. */
public interface TripAssistantOutputBoundary {
    void presentSuccess(TripAssistantOutputData outputData);

    void presentFailure(String message);
}
