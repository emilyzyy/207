package use_case.usecases;

/** Output boundary that lets an adapter present share results without framework coupling. */
public interface ShareTripOutputBoundary {
    /**
     * Performs the p re se nt su cc es s operation.
     * @param shareText the s ha re te xt value
     */
    void presentSuccess(String shareText);

    /**
     * Performs the p re se nt fa il ur e operation.
     * @param errorMessage the e rr or me ss ag e value
     */
    void presentFailure(String errorMessage);
}
