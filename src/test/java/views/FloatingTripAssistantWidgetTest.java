package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import entity.valueobjects.TripAssistantMessage;
import interface_adapter.controllers.TaskRunner;
import interface_adapter.controllers.TripAssistantController;
import interface_adapter.presenters.TripAssistantPresenter;
import interface_adapter.viewmodels.TripAssistantState;
import interface_adapter.viewmodels.TripAssistantViewModel;

final class FloatingTripAssistantWidgetTest {

    @Test
    void avatarAndWideHeaderTogglePreserveHistory() throws Exception {
        final AtomicReference<FloatingTripAssistantWidget> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            final FloatingTripAssistantWidget widget = widget();
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
            assertTrue(widget.getAssistantPanel().getMinimizeButton()
                    .getPreferredSize().width >= 48);
            widget.getAssistantPanel().getMinimizeButton().doClick();
            assertFalse(widget.isExpanded());
            assertTrue(widget.getAssistantPanel().getHistoryArea()
                    .getText().contains("Hi, I'm George."));
        });

        final FloatingTripAssistantWidget widget = reference.get();
        assertEquals("Open George chat", widget.getAvatarButton()
                .getAccessibleContext().getAccessibleName());
    }

    @Test
    void panelAndAvatarStayBottomRightAndWithinBoundsAfterResize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final FloatingTripAssistantWidget widget = widget();
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
        final Rectangle avatar = widget.getAvatarButton().getBounds();
        final Rectangle panel = widget.getAssistantPanel().getBounds();

        assertWithin(avatar, width, height);
        assertWithin(panel, width, height);
        assertEquals(width - FloatingTripAssistantWidget.EDGE_GAP,
                avatar.x + avatar.width);
        assertEquals(height - FloatingTripAssistantWidget.AVATAR_BOTTOM_GAP,
                avatar.y + avatar.height);
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
        final TripAssistantViewModel viewModel = new TripAssistantViewModel(
                new TripAssistantState(Collections.singletonList(
                        new TripAssistantMessage(
                                TripAssistantMessage.Role.ASSISTANT,
                                "Hi, I'm George.")), false, ""));
        final TripAssistantPresenter presenter = new TripAssistantPresenter(viewModel);
        final TripAssistantController controller = new TripAssistantController(
                input -> { }, () -> "trip-1", presenter, viewModel,
                TaskRunner.immediate());
        final TripAssistantPanel panel = new TripAssistantPanel(viewModel, controller);
        return new FloatingTripAssistantWidget(new JPanel(), panel);
    }
}
