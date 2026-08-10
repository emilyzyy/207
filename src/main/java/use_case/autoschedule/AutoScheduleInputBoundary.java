package use_case.autoschedule;

import entity.valueobjects.WeatherOption;

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

    /**
     * Whether the weather preference can be offered for this trip, so the settings dialog
     * knows whether to enable its checkbox.
     *
     * <p>A read-only question with no side effects, which is why it answers directly
     * rather than through the presenter: nothing about the Day Plan changes, and there is
     * no state for a view model to hold. Routing it through the use case rather than
     * letting the dialog ask a forecast service keeps the UI ignorant of who supplies
     * weather, and keeps this class the only place that decides what counts as usable.</p>
     *
     * <p>Never throws. An unanswerable question yields an unavailable option.</p>
     */
    WeatherOption weatherOptionFor(String tripId);

    /**
     * Removes one activity from the unsaved proposal, leaving everything else where it is.
     *
     * <p>Deliberately not a re-run of the search. The traveller took one thing out of a
     * proposal they were reading; re-solving would hand back a different day and lose the
     * arrangement they had just decided they liked. So the remaining activities keep their
     * proposed times and order, and only the journeys either side of the removed activity are
     * rebuilt.</p>
     *
     * <p>Nothing is saved. The repository is untouched, Apply still has to be pressed, and
     * Cancel still restores the day exactly as it was.</p>
     */
    void removeFromProposal(ProposalEditInputData inputData);
}
