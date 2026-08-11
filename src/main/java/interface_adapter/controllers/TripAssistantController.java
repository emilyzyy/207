package interface_adapter.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import entity.valueobjects.TripAssistantMessage;
import interface_adapter.presenters.TripAssistantPresenter;
import interface_adapter.viewmodels.TripAssistantViewModel;
import use_case.tripassistant.TripAssistantInputBoundary;
import use_case.tripassistant.TripAssistantInputData;

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

    /**
     * Performs the e xe cu te operation.
     * @param question the q ue st io n value
     */
    public void execute(String question) {
        final String normalized = question == null ? "" : question.trim();
        if (normalized.isEmpty()) {
            presenter.presentFailure("Type a question for George");
            return;
        }
        final List<TripAssistantMessage> history = new ArrayList<TripAssistantMessage>(
                viewModel.getState().getMessages());
        final String currentTripId = tripId.get();
        presenter.presentLoading(normalized);
        taskRunner.run(() -> {
            inputBoundary.execute(
                    new TripAssistantInputData(currentTripId, normalized, history));
        });
    }
}
