package interface_adapter.presenters;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import entity.valueobjects.TripAssistantMessage;
import interface_adapter.viewmodels.TripAssistantState;
import interface_adapter.viewmodels.TripAssistantViewModel;
import use_case.tripassistant.TripAssistantOutputBoundary;
import use_case.tripassistant.TripAssistantOutputData;

/** Maps use-case results into George's Swing chat state. */
public final class TripAssistantPresenter implements TripAssistantOutputBoundary {
    private final TripAssistantViewModel viewModel;

    public TripAssistantPresenter(TripAssistantViewModel viewModel) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Trip Assistant ViewModel is required");
        }
        this.viewModel = viewModel;
    }

    /**
     * Performs the p re se nt lo ad in g operation.
     * @param question the q ue st io n value
     */
    public void presentLoading(String question) {
        update(() -> {
            final List<TripAssistantMessage> messages = mutableMessages();
            messages.add(new TripAssistantMessage(TripAssistantMessage.Role.USER, question));
            viewModel.setState(new TripAssistantState(messages, true, ""));
        });
    }

    @Override
    public void presentSuccess(TripAssistantOutputData outputData) {
        update(() -> {
            final List<TripAssistantMessage> messages = mutableMessages();
            messages.add(new TripAssistantMessage(
                    TripAssistantMessage.Role.ASSISTANT,
                    outputData.getAnswer(), outputData.getActivityIds()));
            viewModel.setState(new TripAssistantState(messages, false, ""));
        });
    }

    @Override
    public void presentFailure(String message) {
        update(() -> viewModel.setState(new TripAssistantState(
                viewModel.getState().getMessages(), false,
                message == null || message.trim().isEmpty()
                        ? "George couldn't answer right now." : message)));
    }

    private List<TripAssistantMessage> mutableMessages() {
        return new ArrayList<TripAssistantMessage>(viewModel.getState().getMessages());
    }

    private void update(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not update Trip Assistant view", exception);
        }
    }
}
