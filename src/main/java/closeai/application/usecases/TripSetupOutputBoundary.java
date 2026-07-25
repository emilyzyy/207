package closeai.application.usecases;

/** Output boundary shared by the Swing create/edit trip setup workflow. */
public interface TripSetupOutputBoundary {
    void presentSuccess(TripSetupOutputData outputData);

    void presentFailure(String errorMessage);
}
