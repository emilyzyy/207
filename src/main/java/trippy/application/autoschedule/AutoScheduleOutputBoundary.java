package trippy.application.autoschedule;

/**
 * How the use case reports back, implemented by the Presenter.
 *
 * <p>Four distinct outcomes rather than one nullable result, so the Presenter cannot
 * accidentally treat a conflict as a success. A conflict means the day genuinely cannot
 * be arranged; a failure means the request itself was unusable.</p>
 */
public interface AutoScheduleOutputBoundary {

    void presentPreview(AutoSchedulePreviewOutputData outputData);

    void presentApplied(AutoScheduleAppliedOutputData outputData);

    /** Expected infeasibility: nothing was changed, and the reason is specific. */
    void presentConflict(AutoScheduleConflictOutputData outputData);

    /** Invalid input or an unavailable dependency. */
    void presentFailure(String message);
}
