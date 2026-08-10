package use_case.autoschedule;

/**
 * How the use case reports back, implemented by the Presenter.
 *
 * <p>Four distinct outcomes rather than one nullable result, so the Presenter cannot
 * accidentally treat a conflict as a success. A conflict means the day genuinely cannot
 * be arranged; a failure means the request itself was unusable.</p>
 */
public interface AutoScheduleOutputBoundary {

    /**
     * Performs the p re se nt pr ev ie w operation.
     * @param outputData the o ut pu td at a value
     */
    void presentPreview(AutoSchedulePreviewOutputData outputData);

    /**
     * Performs the p re se nt ap pl ie d operation.
     * @param outputData the o ut pu td at a value
     */
    void presentApplied(AutoScheduleAppliedOutputData outputData);

    /**
     * Expected infeasibility: nothing was changed, and the reason is specific.
     * @param outputData the o ut pu td at a value
     */
    void presentConflict(AutoScheduleConflictOutputData outputData);

    /**
     * Invalid input or an unavailable dependency.
     * @param message the m es sa ge value
     */
    void presentFailure(String message);
}
