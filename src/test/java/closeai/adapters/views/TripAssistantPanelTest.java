package closeai.adapters.views;

import closeai.adapters.controllers.TaskRunner;
import closeai.adapters.controllers.TripAssistantController;
import closeai.adapters.presenters.TripAssistantPresenter;
import closeai.adapters.viewmodels.TripAssistantState;
import closeai.adapters.viewmodels.TripAssistantViewModel;
import closeai.application.tripassistant.TripAssistantMessage;
import closeai.application.tripassistant.TripAssistantOutputData;
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
        assertEquals("Close George chat", panel.getCloseButton()
                .getAccessibleContext().getAccessibleName());
    }
}
