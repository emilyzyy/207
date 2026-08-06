package closeai.application.autoschedule;

/**
 * The Autoschedule use case as its callers see it.
 *
 * <p>Implemented by the Interactor and called by the Controller, so the outer layers
 * depend on this interface rather than on the use case's internals.</p>
 */
public interface AutoScheduleInputBoundary {

    /** Works out a proposed schedule and presents it. Changes nothing. */
    void preview(AutoScheduleInputData inputData);

    /** Saves a previously previewed schedule, if the Day Plan has not moved on. */
    void apply(AutoScheduleApplyInputData inputData);
}
