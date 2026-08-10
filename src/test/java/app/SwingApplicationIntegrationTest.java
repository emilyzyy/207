package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import entity.entities.Trip;
import entity.valueobjects.TransportationMode;
import interface_adapter.viewmodels.DayPlanViewModel;
import views.TrippyFrame;

final class SwingApplicationIntegrationTest {

    @Test
    void existingTripUsesDayPlanOptionsPopupAndRemainsUsable() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        System.setProperty("trippy.weather.mode", "mock");
        System.setProperty("trippy.places.mode", "mock");
        System.setProperty("trippy.map.tiles.mode", "offline");

        SwingUtilities.invokeAndWait(() -> {
            final AppBuilder builder = new AppBuilder();
            final AppContainer app = builder.buildOffline();
            final Trip created = app.createTrip.execute(
                    "Toronto", java.time.LocalDate.of(2026, 8, 2),
                    java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0),
                    TransportationMode.WALKING);
            final TrippyFrame frame = builder.buildFrameForTrip(app, created);
            final JTabbedPane tabs = findTabs(frame);
            assertNotNull(tabs);
            assertEquals(3, tabs.getTabCount());
            assertEquals("Search", tabs.getTitleAt(0));
            assertEquals("Bookmarks", tabs.getTitleAt(1));
            assertEquals("Day Plan", tabs.getTitleAt(2));
            assertSame(frame.getTripAssistantPanel(),
                    frame.getTripAssistantWidget().getAssistantPanel());
            assertTrue(frame.getTripAssistantPanel().getHistoryArea()
                    .getText().contains("George"));
            assertNotNull(findButton(frame.getTripAssistantPanel(), "Send"));
            assertTrue(frame.getTripAssistantWidget().getAvatarButton().isVisible());
            assertFalse(frame.getTripAssistantWidget().isExpanded());
            frame.getTripAssistantWidget().getAvatarButton().doClick();
            assertTrue(frame.getTripAssistantWidget().isExpanded());
            frame.getTripAssistantPanel().getMinimizeButton().doClick();
            assertFalse(frame.getTripAssistantWidget().isExpanded());
            assertTrue(frame.getTripAssistantPanel().getHistoryArea()
                    .getText().contains("George"));

            final DayPlanViewModel sharedState = frame.getCalendarDialog().getViewModel();
            assertSame(sharedState, frame.getDayPlanPanel().getViewModel());
            assertEquals(created.getId(), sharedState.getState().getTripId());
            final AbstractButton autoschedule = findButton(frame, "Autoschedule");
            assertNotNull(autoschedule, "Autoschedule should be the Day Plan action");
            assertTrue(autoschedule.isEnabled());
            final AbstractButton options = findButton(frame, "Options");
            assertNotNull(options, "Trip options should be available from the Day Plan");
            assertTrue(options.isEnabled());
            final AbstractButton share = findButton(frame, "Share");
            assertNotNull(share);
            assertTrue(share.isEnabled());
            share.doClick();
            assertTrue(frame.getShareDialog().isVisible());
            assertTrue(frame.getShareDialog().getViewModel()
                    .getState().getShareText().contains("Trippy trip to Toronto"));
            assertEquals(created.getDate(), frame.getCalendarDialog()
                    .getCalendarViewModel().getState().getTripDate());

            final AbstractButton calendar = findButton(frame, "Calendar View");
            assertNotNull(calendar);
            calendar.doClick();
            assertTrue(frame.getCalendarDialog().isVisible());

            tabs.setSelectedIndex(2);
            // Clicking Autoschedule opens a modal settings dialog, which would block this
            // event-thread test; the settings-to-preview path is covered by the controller
            // and interactor tests instead.
            assertTrue(autoschedule.isEnabled());
            assertNull(findButton(frame, "Optimize Itinerary"),
                    "the replaced mockup path should not be reachable anywhere in the frame");
            frame.dispose();
        });
    }

    private static JTabbedPane findTabs(Component component) {
        if (component instanceof JTabbedPane) {
            return (JTabbedPane) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                final JTabbedPane found = findTabs(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<JTextField> findTextFields(Component component) {
        final List<JTextField> result = new ArrayList<JTextField>();
        collectTextFields(component, result);
        return result;
    }

    private static <T extends Component> T findComponent(Component component, Class<T> type) {
        final List<T> matches = findComponents(component, type);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static <T extends Component> List<T> findComponents(
            Component component, Class<T> type) {
        final List<T> matches = new ArrayList<>();
        collectComponents(component, type, matches);
        return matches;
    }

    private static <T extends Component> void collectComponents(
            Component component, Class<T> type, List<T> matches) {
        if (type.isInstance(component)) {
            matches.add(type.cast(component));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectComponents(child, type, matches);
            }
        }
    }

    private static void collectTextFields(
            Component component, List<JTextField> result) {
        if (component instanceof JTextField) {
            result.add((JTextField) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectTextFields(child, result);
            }
        }
    }

    private static AbstractButton findButton(Component component, String text) {
        if (component instanceof AbstractButton
                && text.equals(((AbstractButton) component).getText())) {
            return (AbstractButton) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                final AbstractButton found = findButton(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
