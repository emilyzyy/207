package trippy.adapters.presenters;

import trippy.adapters.viewmodels.TripAssistantState;
import trippy.adapters.viewmodels.TripAssistantViewModel;
import trippy.application.tripassistant.TripAssistantMessage;
import trippy.application.tripassistant.TripAssistantOutputBoundary;
import trippy.application.tripassistant.TripAssistantOutputData;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/** Maps use-case results into George's Swing chat state. */
public final class TripAssistantPresenter implements TripAssistantOutputBoundary {
    private final TripAssistantViewModel viewModel;

    public TripAssistantPresenter(TripAssistantViewModel viewModel) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Trip Assistant ViewModel is required");
        }
        this.viewModel = viewModel;
    }

    public void presentLoading(String question) {
        update(() -> {
            List<TripAssistantMessage> messages = mutableMessages();
            messages.add(new TripAssistantMessage(TripAssistantMessage.Role.USER, question));
            viewModel.setState(new TripAssistantState(messages, true, ""));
        });
    }

    @Override
    public void presentSuccess(TripAssistantOutputData outputData) {
        update(() -> {
            List<TripAssistantMessage> messages = mutableMessages();
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
        } catch (Exception exception) {
            throw new IllegalStateException("Could not update Trip Assistant view", exception);
        }
    }
}
