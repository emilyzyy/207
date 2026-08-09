package views;

import interface_adapter.controllers.TaskRunner;
import interface_adapter.controllers.TripAssistantController;
import interface_adapter.presenters.TripAssistantPresenter;
import interface_adapter.viewmodels.TripAssistantState;
import interface_adapter.viewmodels.TripAssistantViewModel;
import use_case.tripassistant.TripAssistantMessage;
import use_case.tripassistant.TripAssistantOutputData;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TripAssistantPanelTest {

    @Test
    void displaysHistorySendsQuestionsAndRendersLoadingAndErrors() throws Exception {
        TripAssistantViewModel viewModel = new TripAssistantViewModel(
                new TripAssistantState(Collections.singletonList(
                        new TripAssistantMessage(
                                TripAssistantMessage.Role.ASSISTANT,
                                "Hi, I'm George.")), false, ""));
        TripAssistantPresenter presenter = new TripAssistantPresenter(viewModel);
        AtomicReference<String> question = new AtomicReference<String>();
        TripAssistantController controller = new TripAssistantController(input -> {
            question.set(input.getQuestion());
            presenter.presentSuccess(new TripAssistantOutputData(
                    "Try Actual Museum.", Collections.singletonList("museum")));
        }, () -> "trip-1", presenter, viewModel, TaskRunner.immediate());
        AtomicReference<TripAssistantPanel> panelReference = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            TripAssistantPanel panel = new TripAssistantPanel(viewModel, controller);
            panelReference.set(panel);
            assertTrue(panel.getHistoryArea().getText().contains("George"));
            panel.getInputField().setText("What do you recommend?");
            panel.getSendButton().doClick();
        });

        TripAssistantPanel panel = panelReference.get();
        assertEquals("What do you recommend?", question.get());
        assertTrue(panel.getHistoryArea().getText().contains("Actual Museum"));
        SwingUtilities.invokeAndWait(() -> {
            panel.getInputField().setText("What works in rain?");
            panel.getInputField().postActionEvent();
        });
        assertEquals("What works in rain?", question.get());
        SwingUtilities.invokeAndWait(() -> viewModel.setState(new TripAssistantState(
                viewModel.getState().getMessages(), true, "")));
        assertTrue(panel.isLoadingVisible());
        assertFalse(panel.getSendButton().isEnabled());
        SwingUtilities.invokeAndWait(() -> presenter.presentFailure(
                "Open or create a trip before asking George"));
        assertFalse(panel.isLoadingVisible());
        assertTrue(panel.getErrorLabel().getText().contains("Open or create"));
        assertEquals("George chat history", panel.getHistoryArea()
                .getAccessibleContext().getAccessibleName());
        assertEquals("Message George", panel.getInputField()
                .getAccessibleContext().getAccessibleName());
        assertEquals("Send message to George", panel.getSendButton()
                .getAccessibleContext().getAccessibleName());
        assertEquals("Minimize George chat", panel.getMinimizeButton()
                .getAccessibleContext().getAccessibleName());
    }
}
