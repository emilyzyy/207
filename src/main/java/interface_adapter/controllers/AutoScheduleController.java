package interface_adapter.controllers;

import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.PreviewRowView;
import use_case.autoschedule.AutoScheduleApplyInputData;
import use_case.autoschedule.AutoScheduleInputBoundary;
import use_case.autoschedule.AutoScheduleInputData;
import use_case.autoschedule.ProposedEventData;
import use_case.autoschedule.TimeWindow;
import entity.valueobjects.WeatherOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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
                settings.isKeepCurrentOrder(), settings.isConsiderWeather(),
                settings.isMinimizeTravel(), settings.isMinimizeGaps(),
                settings.isPreserveMealtimes(), settings.isPreferDaylight());

        viewModel.setState(state.loading("Working out a better arrangement..."));
        taskRunner.run(() -> autoSchedule.preview(input));
    }

    /**
     * Asks whether the weather preference can be offered, and hands the answer back.
     *
     * <p>Answering means asking a forecast provider, which is a network call, so it runs
     * on the task runner exactly as Preview does — the settings dialog opens immediately
     * with the checkbox disabled and fills it in when the answer arrives, rather than
     * making the traveller wait on a frozen window for a checkbox.</p>
     *
     * <p>The callback is invoked on the background thread. Marshalling back to the event
     * thread is the view's business, since it is the view that knows it is Swing.</p>
     */
    public void loadWeatherOption(Consumer<WeatherOption> onAnswered) {
        if (onAnswered == null) {
            return;
        }
        String tripId = viewModel.getState().getTripId();
        if (tripId.isEmpty()) {
            onAnswered.accept(WeatherOption.unavailable(WeatherOption.NO_FORECAST));
            return;
        }
        taskRunner.run(() -> onAnswered.accept(autoSchedule.weatherOptionFor(tripId)));
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
