package trippy.adapters.controllers;

import trippy.adapters.presenters.TripAssistantPresenter;
import trippy.adapters.viewmodels.TripAssistantViewModel;
import trippy.application.tripassistant.TripAssistantInputBoundary;
import trippy.application.tripassistant.TripAssistantInputData;
import trippy.application.tripassistant.TripAssistantMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Captures one chat turn and dispatches all potentially slow work away from Swing's EDT. */
public final class TripAssistantController {
    private final TripAssistantInputBoundary inputBoundary;
    private final Supplier<String> tripId;
    private final TripAssistantPresenter presenter;
    private final TripAssistantViewModel viewModel;
    private final TaskRunner taskRunner;

    public TripAssistantController(
            TripAssistantInputBoundary inputBoundary, Supplier<String> tripId,
            TripAssistantPresenter presenter, TripAssistantViewModel viewModel,
            TaskRunner taskRunner) {
        if (inputBoundary == null || tripId == null || presenter == null
                || viewModel == null || taskRunner == null) {
            throw new IllegalArgumentException("Trip Assistant controller dependencies are required");
        }
        this.inputBoundary = inputBoundary;
        this.tripId = tripId;
        this.presenter = presenter;
        this.viewModel = viewModel;
        this.taskRunner = taskRunner;
    }

    public void execute(String question) {
        String normalized = question == null ? "" : question.trim();
        if (normalized.isEmpty()) {
            presenter.presentFailure("Type a question for George");
            return;
        }
        List<TripAssistantMessage> history = new ArrayList<TripAssistantMessage>(
                viewModel.getState().getMessages());
        String currentTripId = tripId.get();
        presenter.presentLoading(normalized);
        taskRunner.run(() -> inputBoundary.execute(
                new TripAssistantInputData(currentTripId, normalized, history)));
    }
}
