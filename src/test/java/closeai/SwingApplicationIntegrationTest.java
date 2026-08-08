package closeai;

import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.views.CloseAIFrame;
import closeai.adapters.views.DateSelectionButton;
import closeai.adapters.views.TimeSelectorPanel;
import closeai.application.AppContainer;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

final class SwingApplicationIntegrationTest {

    @Test
    void userCanCreateEditAndUseTheSameTripInOptimize() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        System.setProperty("closeai.weather.mode", "mock");
        System.setProperty("closeai.places.mode", "mock");
        System.setProperty("closeai.map.tiles.mode", "offline");

        SwingUtilities.invokeAndWait(() -> {
            AppBuilder builder = new AppBuilder();
            AppContainer app = builder.buildOffline();
            CloseAIFrame frame = builder.buildSwingApplication(app);
            JTabbedPane tabs = findTabs(frame);
            assertNotNull(tabs);
            assertEquals(4, tabs.getTabCount());
            assertEquals("Trip Options", tabs.getTitleAt(3));
            assertSame(frame.getTripAssistantPanel(),
                    frame.getTripAssistantWidget().getAssistantPanel());
            assertTrue(frame.getTripAssistantPanel().getHistoryArea()
                    .getText().contains("George"));
            assertNotNull(findButton(frame.getTripAssistantPanel(), "Send"));
            assertTrue(frame.getTripAssistantWidget().getAvatarButton().isVisible());
            assertFalse(frame.getTripAssistantWidget().isExpanded());
            frame.getTripAssistantWidget().getAvatarButton().doClick();
            assertTrue(frame.getTripAssistantWidget().isExpanded());
            frame.getTripAssistantPanel().getCloseButton().doClick();
            assertFalse(frame.getTripAssistantWidget().isExpanded());
            assertTrue(frame.getTripAssistantPanel().getHistoryArea()
                    .getText().contains("George"));

            DayPlanViewModel sharedState = frame.getCalendarDialog().getViewModel();
            assertSame(sharedState, frame.getDayPlanPanel().getViewModel());
            assertEquals("", sharedState.getState().getTripId());
            AbstractButton autoschedule = findButton(frame, "Autoschedule");
            assertNotNull(autoschedule, "Autoschedule should be the Day Plan action");
            assertFalse(autoschedule.isEnabled(), "no trip yet, so there is nothing to arrange");
            AbstractButton share = findButton(frame, "Share");
            assertNotNull(share);
            assertFalse(share.isEnabled());

            tabs.setSelectedIndex(3);
            Container tripSetup = (Container) tabs.getComponentAt(3);
            List<JTextField> fields = findTextFields(tripSetup);
            assertEquals(1, fields.size());
            fields.get(0).setText("Toronto");
            DateSelectionButton date = findComponent(tripSetup, DateSelectionButton.class);
            List<TimeSelectorPanel> times = findComponents(tripSetup, TimeSelectorPanel.class);
            assertNotNull(date);
            assertEquals(2, times.size());
            date.setDate(java.time.LocalDate.of(2026, 8, 2));
            times.get(0).setTime(java.time.LocalTime.of(9, 0));
            times.get(1).setTime(java.time.LocalTime.of(18, 0));

            AbstractButton create = findButton(tripSetup, "Create Trip");
            assertNotNull(create);
            create.doClick();

            String tripId = sharedState.getState().getTripId();
            assertFalse(tripId.isEmpty());
            Trip created = app.trips.findById(tripId).orElseThrow();
            assertEquals("Toronto", created.getDestination());
            assertFalse(fields.get(0).isEditable(),
                    "destination becomes read-only after the day plan is created");
            assertEquals(TransportationMode.WALKING,
                    created.getTransportationMode());
            assertTrue(autoschedule.isEnabled(), "a trip exists, so Autoschedule is available");

            assertTrue(share.isEnabled());
            share.doClick();
            assertTrue(frame.getShareDialog().isVisible());
            assertTrue(frame.getShareDialog().getViewModel()
                    .getState().getShareText().contains("CloseAI trip to Toronto"));
            assertEquals(created.getDate(), frame.getCalendarDialog()
                    .getCalendarViewModel().getState().getTripDate());

            AbstractButton calendar = findButton(frame, "Calendar View");
            assertNotNull(calendar);
            calendar.doClick();
            assertTrue(frame.getCalendarDialog().isVisible());

            date.setDate(java.time.LocalDate.of(2026, 8, 3));
            times.get(0).setTime(java.time.LocalTime.of(10, 0));
            times.get(1).setTime(java.time.LocalTime.of(19, 30));
            AbstractButton save = findButton(tripSetup, "Save Trip Options");
            assertNotNull(save);
            save.doClick();
            Trip updated = app.trips.findById(tripId).orElseThrow();
            assertEquals("Toronto", updated.getDestination());
            assertEquals(java.time.LocalDate.of(2026, 8, 3), updated.getDate());
            assertEquals(java.time.LocalTime.of(10, 0), updated.getStartTime());
            assertEquals(java.time.LocalTime.of(19, 30), updated.getEndTime());
            assertEquals(updated.getDate(), frame.getCalendarDialog()
                    .getCalendarViewModel().getState().getTripDate());

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
                JTabbedPane found = findTabs(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<JTextField> findTextFields(Component component) {
        List<JTextField> result = new ArrayList<JTextField>();
        collectTextFields(component, result);
        return result;
    }

    private static <T extends Component> T findComponent(Component component, Class<T> type) {
        List<T> matches = findComponents(component, type);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static <T extends Component> List<T> findComponents(
            Component component, Class<T> type) {
        List<T> matches = new ArrayList<>();
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
                AbstractButton found = findButton(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
