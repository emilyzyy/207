package use_case.usecases;

/** Output boundary that lets an adapter present share results without framework coupling. */
public interface ShareTripOutputBoundary {
    void presentSuccess(String shareText);

    void presentFailure(String errorMessage);
}
