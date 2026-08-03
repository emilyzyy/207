package closeai;

import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.views.CloseAIFrame;
import closeai.application.AppContainer;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

            DayPlanViewModel sharedState = frame.getCalendarDialog().getViewModel();
            assertSame(sharedState, frame.getDayPlanPanel().getViewModel());
            assertEquals("", sharedState.getState().getTripId());
            AbstractButton optimize = findButton(frame, "Optimize Itinerary");
            assertNotNull(optimize);
            assertFalse(optimize.isEnabled());
            AbstractButton share = findButton(frame, "Share");
            assertNotNull(share);
            assertFalse(share.isEnabled());

            tabs.setSelectedIndex(3);
            Container tripSetup = (Container) tabs.getComponentAt(3);
            List<JTextField> fields = findTextFields(tripSetup);
            assertEquals(4, fields.size());
            fields.get(0).setText("Toronto");
            fields.get(1).setText("2026-08-02");
            fields.get(2).setText("09:00");
            fields.get(3).setText("18:00");
            JComboBox<?> transportation = findComboBox(tripSetup);
            assertNotNull(transportation);
            transportation.setSelectedItem(TransportationMode.TRANSIT);

            AbstractButton create = findButton(tripSetup, "Create Trip");
            assertNotNull(create);
            create.doClick();

            String tripId = sharedState.getState().getTripId();
            assertFalse(tripId.isEmpty());
            Trip created = app.trips.findById(tripId).orElseThrow();
            assertEquals("Toronto", created.getDestination());
            assertEquals(TransportationMode.TRANSIT,
                    created.getTransportationMode());
            assertTrue(optimize.isEnabled());

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

            fields.get(0).setText("Ottawa");
            AbstractButton save = findButton(tripSetup, "Save Trip Options");
            assertNotNull(save);
            save.doClick();
            assertEquals("Ottawa",
                    app.trips.findById(tripId).orElseThrow().getDestination());

            tabs.setSelectedIndex(2);
            optimize.doClick();
            assertEquals(
                    "Add activities to the Day Plan before optimizing",
                    sharedState.getState().getMessage());
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

    private static JComboBox<?> findComboBox(Component component) {
        if (component instanceof JComboBox) {
            return (JComboBox<?>) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JComboBox<?> found = findComboBox(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
