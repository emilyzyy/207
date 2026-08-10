package use_case.autoschedule;

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

    /**
     * A draft edit that could not be made, with the proposal left exactly as it was.
     *
     * <p>Distinct from a failure because nothing is broken and nothing needs re-running: the
     * Preview is still on screen, still applicable, and the traveller can remove something
     * else or carry on.</p>
     */
    void presentDraftEditRefused(String reason);

    /**
     * A proposal the traveller has edited by hand, rather than one the search produced.
     *
     * <p>Separate from {@link #presentPreview} because the explanations are carried forward
     * rather than recomputed from a plan: the remaining activities kept the times the search
     * gave them, so every per-activity reason and every policy improvement about them is still
     * exactly as true as it was. Only the measurable figures changed, and only what named the
     * removed activity is dropped.</p>
     */
    void presentEditedPreview(AutoSchedulePreviewOutputData outputData, String removedEventId);
}
