package closeai.adapters.views;

import closeai.adapters.controllers.TaskRunner;
import closeai.adapters.controllers.TripAssistantController;
import closeai.adapters.presenters.TripAssistantPresenter;
import closeai.adapters.viewmodels.TripAssistantState;
import closeai.adapters.viewmodels.TripAssistantViewModel;
import closeai.application.tripassistant.TripAssistantMessage;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FloatingTripAssistantWidgetTest {

    @Test
    void avatarTogglesPanelAndBothHeaderControlsPreserveHistory() throws Exception {
        AtomicReference<FloatingTripAssistantWidget> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            FloatingTripAssistantWidget widget = widget();
            widget.setSize(900, 700);
            widget.doLayout();
            reference.set(widget);

            assertTrue(widget.getAvatarButton().isVisible());
            assertNotNull(widget.getAvatarButton().getIcon());
            assertEquals(48, widget.getAvatarButton().getIcon().getIconWidth());
            assertEquals(48, widget.getAvatarButton().getIcon().getIconHeight());
            assertFalse(widget.isExpanded());
            assertFalse(widget.getAssistantPanel().isVisible());

            widget.getAvatarButton().doClick();
            assertTrue(widget.isExpanded());
            assertTrue(widget.getAssistantPanel().isVisible());
            assertTrue(widget.getAssistantPanel().getHistoryArea()
                    .getText().contains("Hi, I'm George."));

            widget.getAssistantPanel().getMinimizeButton().doClick();
            assertFalse(widget.isExpanded());
            widget.getAvatarButton().doClick();
            widget.getAssistantPanel().getCloseButton().doClick();
            assertFalse(widget.isExpanded());
            assertTrue(widget.getAssistantPanel().getHistoryArea()
                    .getText().contains("Hi, I'm George."));
        });

        FloatingTripAssistantWidget widget = reference.get();
        assertEquals("Open George chat", widget.getAvatarButton()
                .getAccessibleContext().getAccessibleName());
    }

    @Test
    void panelAndAvatarStayBottomRightAndWithinBoundsAfterResize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FloatingTripAssistantWidget widget = widget();
            widget.setExpanded(true);
            assertAnchored(widget, 900, 700);
            assertAnchored(widget, 520, 430);
            assertAnchored(widget, 320, 280);
        });
    }

    private void assertAnchored(
            FloatingTripAssistantWidget widget, int width, int height) {
        widget.setSize(width, height);
        widget.doLayout();
        Rectangle avatar = widget.getAvatarButton().getBounds();
        Rectangle panel = widget.getAssistantPanel().getBounds();

        assertWithin(avatar, width, height);
        assertWithin(panel, width, height);
        assertEquals(width - FloatingTripAssistantWidget.EDGE_GAP,
                avatar.x + avatar.width);
        assertEquals(width - FloatingTripAssistantWidget.EDGE_GAP,
                panel.x + panel.width);
        assertTrue(panel.y + panel.height
                <= avatar.y - FloatingTripAssistantWidget.PANEL_GAP);
    }

    private void assertWithin(Rectangle bounds, int width, int height) {
        assertTrue(bounds.x >= 0);
        assertTrue(bounds.y >= 0);
        assertTrue(bounds.x + bounds.width <= width);
        assertTrue(bounds.y + bounds.height <= height);
    }

    private FloatingTripAssistantWidget widget() {
        TripAssistantViewModel viewModel = new TripAssistantViewModel(
                new TripAssistantState(Collections.singletonList(
                        new TripAssistantMessage(
                                TripAssistantMessage.Role.ASSISTANT,
                                "Hi, I'm George.")), false, ""));
        TripAssistantPresenter presenter = new TripAssistantPresenter(viewModel);
        TripAssistantController controller = new TripAssistantController(
                input -> { }, () -> "trip-1", presenter, viewModel,
                TaskRunner.immediate());
        TripAssistantPanel panel = new TripAssistantPanel(viewModel, controller);
        return new FloatingTripAssistantWidget(new JPanel(), panel);
    }
}
