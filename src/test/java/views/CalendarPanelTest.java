package views;

import interface_adapter.viewmodels.CalendarViewMode;
import interface_adapter.viewmodels.CalendarViewModel;
import interface_adapter.viewmodels.DashboardState;
import interface_adapter.viewmodels.DashboardViewModel;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CalendarPanelTest {

    @Test
    void exposesInteractiveDayWeekAndMonthControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LocalDate tripDate = LocalDate.of(2026, 8, 12);
            DashboardViewModel dashboard = new DashboardViewModel(
                    new DashboardState("Toronto", tripDate, "Clear", ""));
            ScheduledEvent travel = new ScheduledEvent(
                    "travel", null, LocalTime.of(9, 0), LocalTime.of(9, 15),
                    EventType.TRAVEL, "Walk to museum");
            DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                    "trip-1", Collections.singletonList(travel), "", false));
            CalendarViewModel viewModel = new CalendarViewModel(
                    dashboard, dayPlan, () -> LocalDate.of(2026, 8, 3));
            CalendarPanel panel = new CalendarPanel(viewModel);

            assertTrue(allText(panel).contains("August 2026"));
            assertTrue(allText(panel).contains("Toronto"));
            assertTrue(allText(panel).contains("1 item(s)"));
            assertNotNull(findNamed(panel, "calendar-day-2026-08-12"));

            @SuppressWarnings("unchecked")
            JComboBox<CalendarViewMode> selector =
                    (JComboBox<CalendarViewMode>) findNamed(panel, "calendar-view-mode");
            assertNotNull(selector);
            selector.setSelectedItem(CalendarViewMode.DAY);
            assertEquals(CalendarViewMode.DAY, viewModel.getState().getViewMode());
            assertTrue(allText(panel).contains("Wednesday, August 12, 2026"));
            assertTrue(allText(panel).contains("Walk to museum"));

            AbstractButton next = (AbstractButton) findNamed(panel, "calendar-next");
            assertNotNull(next);
            next.doClick();
            assertEquals(LocalDate.of(2026, 8, 13), viewModel.getState().getFocusDate());
            assertTrue(allText(panel).contains("No Trippy trip"));

            selector.setSelectedItem(CalendarViewMode.WEEK);
            assertEquals(CalendarViewMode.WEEK, viewModel.getState().getViewMode());
            AbstractButton tripDateButton =
                    (AbstractButton) findNamed(panel, "calendar-trip-date");
            tripDateButton.doClick();
            assertEquals(tripDate, viewModel.getState().getFocusDate());
            assertTrue(allText(panel).contains("Walk to museum"));
        });
    }

    private static Component findNamed(Component component, String name) {
        if (name.equals(component.getName())) {
            return component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                Component found = findNamed(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String allText(Component component) {
        StringBuilder result = new StringBuilder();
        collectText(component, result);
        return result.toString();
    }

    private static void collectText(Component component, StringBuilder result) {
        if (component instanceof JLabel) {
            result.append(((JLabel) component).getText()).append(' ');
        }
        if (component instanceof AbstractButton) {
            result.append(((AbstractButton) component).getText()).append(' ');
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectText(child, result);
            }
        }
    }
}
