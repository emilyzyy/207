package closeai.adapters.views;

import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.ActivitySelectionViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.adapters.viewmodels.TripOptionsState;
import closeai.adapters.viewmodels.TripOptionsViewModel;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.WeatherSeverity;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SwingPanelStructureTest {

    @Test
    void plannerContainsFourFocusedTabsAndDayPlanObservesSharedState() throws Exception {
        DayPlanViewModel dayPlanViewModel = new DayPlanViewModel(
                new DayPlanState("trip-1", Collections.emptyList(), "", false));
        ActivitySelectionViewModel selection = new ActivitySelectionViewModel();
        SearchPanel search = new SearchPanel(new SearchViewModel(
                new SearchState(Collections.singletonList(activity("rom")), "")),
                null, null, null, selection);
        BookmarksPanel bookmarks = new BookmarksPanel(new BookmarksViewModel(
                new BookmarksState(Collections.singletonList(activity("saved")))),
                null, null, selection);
        DayPlanPanel dayPlan = new DayPlanPanel(
                dayPlanViewModel,
                new closeai.adapters.controllers.AutoScheduleController(
                        new RecordingAutoSchedule(), dayPlanViewModel,
                        closeai.adapters.controllers.TaskRunner.immediate()),
                null, selection);
        TripOptionsPanel options = new TripOptionsPanel(new TripOptionsViewModel(
                new TripOptionsState("Toronto", LocalDate.of(2026, 7, 23),
                        LocalTime.of(9, 0), LocalTime.of(18, 0))));
        PlannerPanel planner = new PlannerPanel(
                search, bookmarks, dayPlan, options);

        JTabbedPane tabs = (JTabbedPane) planner.getComponent(0);
        assertEquals(4, tabs.getTabCount());
        assertEquals("Search", tabs.getTitleAt(0));
        assertEquals("Bookmarks", tabs.getTitleAt(1));
        assertEquals("Day Plan", tabs.getTitleAt(2));
        assertEquals("Trip Options", tabs.getTitleAt(3));
        assertFalse(allText(planner).contains("Trip Assistant"));
        assertTrue(allText(planner).contains("Discover activities"));
        JLabel searchTitle = findLabel(search, "Discover activities");
        JLabel resultCount = findLabel(search, "1 nearby activities");
        assertNotNull(searchTitle);
        assertNotNull(resultCount);
        assertEquals(JLabel.CENTER, searchTitle.getHorizontalAlignment());
        assertEquals(JLabel.CENTER, resultCount.getHorizontalAlignment());
        assertTrue(searchTitle.getParent().getLayout() instanceof java.awt.BorderLayout);
        assertTrue(resultCount.getParent().getLayout() instanceof java.awt.BorderLayout);
        assertNotNull(findComboItem(search, "Filter by rating"));
        assertNotNull(findComboItem(search, "3.5+"));
        assertNotNull(findComboItem(search, "3.0+"));
        assertTrue(allText(planner).contains("Saved for later"));
        assertNotNull(findButton(search, "Add to plan"));
        clickCard(search, "Show rom on the map");
        assertEquals("rom", selection.getSelectedActivityId());
        clickCard(bookmarks, "Show saved on the map");
        assertEquals("saved", selection.getSelectedActivityId());

        AbstractButton autoschedule = findButton(dayPlan, "Autoschedule");
        assertNotNull(autoschedule, "the Day Plan should offer Autoschedule");
        assertTrue(autoschedule.isEnabled());
        assertTrue(autoschedule.isVisible());
        assertTrue(autoschedule.isOpaque());
        assertNull(findButton(dayPlan, "Optimize Itinerary"),
                "the old mockup button should be gone");

        ScheduledEvent event = new ScheduledEvent(
                "event-rom", activity("rom"), LocalTime.of(10, 0),
                LocalTime.of(11, 0), EventType.ACTIVITY, "Visit");
        SwingUtilities.invokeAndWait(() -> dayPlanViewModel.setState(
                new DayPlanState("trip-1", Collections.singletonList(event),
                        "Autoschedule applied. Your Day Plan has been updated.", false,
                        Collections.singletonList(new WeatherWarning(
                                new Location(43.65, -79.38, "Toronto"),
                                LocalTime.of(10, 0), "Rain", WeatherSeverity.MEDIUM,
                                "18°C · 65% precipitation")))));

        assertTrue(allText(dayPlan).contains("rom"));
        assertTrue(allText(dayPlan).contains("Autoschedule applied"));
        // Shiyuan's per-hour forecast lines render on the activity card, now on a 12-hour
        // clock. Asserting the AM is what proves the conversion reached the weather line
        // rather than only the activity times.
        assertTrue(allText(dayPlan).contains("10:00 AM · Rain"));
        assertTrue(allText(dayPlan).contains("65% precipitation"));
        // Alex's per-event controls render alongside the Lock checkbox. This panel is built
        // without a manual controller, so they are present but disabled.
        assertNotNull(findButton(dayPlan, "Edit"));
        assertNotNull(findButton(dayPlan, "Remove"));
        clickCard(dayPlan, "Show rom on the map");
        assertEquals("rom", selection.getSelectedActivityId());
        SwingUtilities.invokeAndWait(() -> { });
        Component timeline = findByName(dayPlan, "Day schedule timeline");
        assertNotNull(timeline);
        assertEquals(1, ((Container) timeline).getComponentCount());
        JScrollPane dayPlanScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(
                JScrollPane.class, timeline);
        assertNotNull(dayPlanScroll);
        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
                dayPlanScroll.getHorizontalScrollBarPolicy());

        SwingUtilities.invokeAndWait(() -> dayPlanViewModel.setState(
                new DayPlanState("trip-1", Collections.singletonList(event),
                        "The itinerary cannot fit inside the trip window", true)));
        assertTrue(allText(dayPlan).contains(
                "The itinerary cannot fit inside the trip window"));
    }

    private Activity activity(String id) {
        return new Activity(id, id, ActivityCategory.ATTRACTION,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(9, 0), LocalTime.of(20, 0),
                IndoorOutdoorType.INDOOR, "Low");
    }

    private String allText(Component component) {
        StringBuilder text = new StringBuilder();
        collectText(component, text);
        return text.toString();
    }

    private void collectText(Component component, StringBuilder text) {
        if (component instanceof javax.swing.JLabel) {
            text.append(((javax.swing.JLabel) component).getText()).append(' ');
        }
        if (component instanceof javax.swing.AbstractButton) {
            text.append(((javax.swing.AbstractButton) component).getText()).append(' ');
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectText(child, text);
            }
        }
    }

    private AbstractButton findButton(Component component, String text) {
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

    private JLabel findLabel(Component component, String text) {
        if (component instanceof JLabel && text.equals(((JLabel) component).getText())) {
            return (JLabel) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JLabel found = findLabel(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JComboBox<?> findComboItem(Component component, String item) {
        if (component instanceof JComboBox) {
            JComboBox<?> combo = (JComboBox<?>) component;
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (item.equals(combo.getItemAt(i))) return combo;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JComboBox<?> found = findComboItem(child, item);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void clickCard(Component component, String tooltip) {
        Component card = findByTooltip(component, tooltip);
        assertNotNull(card);
        MouseEvent click = new MouseEvent(
                card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                0, 5, 5, 1, false);
        for (java.awt.event.MouseListener listener : card.getMouseListeners()) {
            listener.mouseClicked(click);
        }
    }

    private Component findByTooltip(Component component, String tooltip) {
        if (component instanceof javax.swing.JComponent
                && tooltip.equals(((javax.swing.JComponent) component).getToolTipText())) {
            return component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                Component found = findByTooltip(child, tooltip);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Component findByName(Component component, String name) {
        if (name.equals(component.getName())) return component;
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                Component found = findByName(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Stands in for the use case so the panel can be exercised without scheduling. */
    private static final class RecordingAutoSchedule
            implements closeai.application.autoschedule.AutoScheduleInputBoundary {
        @Override
        public void preview(closeai.application.autoschedule.AutoScheduleInputData inputData) {
        }

        @Override
        public void apply(closeai.application.autoschedule.AutoScheduleApplyInputData inputData) {
        }

        @Override
        public closeai.application.autoschedule.WeatherOption weatherOptionFor(String tripId) {
            return closeai.application.autoschedule.WeatherOption.available();
        }
    }
}
