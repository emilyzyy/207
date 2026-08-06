package closeai.adapters.controllers;

import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.PreviewRowView;
import closeai.application.autoschedule.AutoScheduleApplyInputData;
import closeai.application.autoschedule.AutoScheduleInputBoundary;
import closeai.application.autoschedule.AutoScheduleInputData;
import closeai.application.autoschedule.ProposedEventData;
import closeai.application.autoschedule.TimeWindow;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns what the traveller did into a request the use case understands.
 *
 * <p>There is no scheduling thinking here at all: the Controller reads plain values from
 * the dialog and the pinned activities from the view model, hands them across the input
 * boundary, and lets the use case decide everything. Keeping it this thin is what allows
 * the same use case to be driven by a test, or later by a different interface, without
 * any of the logic coming along for the ride.</p>
 */
public final class AutoScheduleController {

    private final AutoScheduleInputBoundary autoSchedule;
    private final DayPlanViewModel viewModel;
    private final TaskRunner taskRunner;

    public AutoScheduleController(AutoScheduleInputBoundary autoSchedule,
                                  DayPlanViewModel viewModel, TaskRunner taskRunner) {
        if (autoSchedule == null || viewModel == null || taskRunner == null) {
            throw new IllegalArgumentException("Autoschedule controller dependencies are required");
        }
        this.autoSchedule = autoSchedule;
        this.viewModel = viewModel;
        this.taskRunner = taskRunner;
    }

    /** Asks for a proposal. Nothing in the itinerary changes as a result of this. */
    public void preview(AutoScheduleSettings settings) {
        DayPlanState state = viewModel.getState();
        if (state.getTripId().isEmpty()) {
            return;
        }

        List<TimeWindow> unavailable = new ArrayList<>();
        for (AutoScheduleSettings.Window window : settings.getUnavailableWindows()) {
            unavailable.add(new TimeWindow(window.getStart(), window.getEnd()));
        }

        AutoScheduleInputData input = new AutoScheduleInputData(state.getTripId(),
                settings.getAvailableStart(), settings.getAvailableEnd(),
                settings.getTransportationMode(), state.getLockedEventIds(), unavailable,
                settings.isKeepCurrentOrder());

        viewModel.setState(state.loading("Working out a better arrangement..."));
        taskRunner.run(() -> autoSchedule.preview(input));
    }

    /** Saves the proposal currently on screen, if the Day Plan has not moved on. */
    public void apply() {
        DayPlanState state = viewModel.getState();
        if (state.getPreviewRows().isEmpty()) {
            return;
        }

        List<ProposedEventData> proposed = new ArrayList<>();
        for (PreviewRowView row : state.getPreviewRows()) {
            proposed.add(new ProposedEventData(row.getEventId(), "", row.getTitle(),
                    row.getKind() == PreviewRowView.Kind.TRAVEL
                            ? ProposedEventData.Kind.TRAVEL : ProposedEventData.Kind.ACTIVITY,
                    row.getStart(), row.getEnd(), row.isLocked(), row.isMoved()));
        }

        AutoScheduleApplyInputData input = new AutoScheduleApplyInputData(state.getTripId(),
                state.getPreviewFingerprint(), proposed);

        viewModel.setState(state.loading("Applying..."));
        taskRunner.run(() -> autoSchedule.apply(input));
    }

    /** Discards the proposal. The itinerary was never touched, so nothing is undone. */
    public void cancel() {
        viewModel.setState(viewModel.getState()
                .clearedPreview("Autoschedule cancelled. Your Day Plan is unchanged."));
    }

    /**
     * Pins or unpins an activity. Pins live with the view for as long as the app is open,
     * so re-running Autoschedule keeps honouring them without changing anything saved.
     */
    public void toggleLock(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return;
        }
        DayPlanState state = viewModel.getState();
        Set<String> locks = new LinkedHashSet<>(state.getLockedEventIds());
        if (!locks.remove(eventId)) {
            locks.add(eventId);
        }
        viewModel.setState(state.withLocks(locks));
    }

    public boolean isLocked(String eventId) {
        return viewModel.getState().getLockedEventIds().contains(eventId);
    }
}
