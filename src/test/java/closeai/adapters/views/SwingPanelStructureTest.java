package closeai.adapters.views;

import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.adapters.viewmodels.TripOptionsState;
import closeai.adapters.viewmodels.TripOptionsViewModel;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import javax.swing.AbstractButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SwingPanelStructureTest {

    @Test
    void plannerContainsFourFocusedTabsAndDayPlanObservesSharedState() throws Exception {
        DayPlanViewModel dayPlanViewModel = new DayPlanViewModel(
                new DayPlanState("trip-1", Collections.emptyList(), "", false));
        SearchPanel search = new SearchPanel(new SearchViewModel(
                new SearchState(Collections.singletonList(activity("rom")), "")));
        BookmarksPanel bookmarks = new BookmarksPanel(new BookmarksViewModel(
                new BookmarksState(Collections.singletonList(activity("saved")))));
        DayPlanPanel dayPlan = new DayPlanPanel(
                dayPlanViewModel,
                new closeai.adapters.controllers.AutoScheduleController(
                        new RecordingAutoSchedule(), dayPlanViewModel,
                        closeai.adapters.controllers.TaskRunner.immediate()));
        TripOptionsPanel options = new TripOptionsPanel(new TripOptionsViewModel(
                new TripOptionsState("Toronto", LocalDate.of(2026, 7, 23),
                        LocalTime.of(9, 0), LocalTime.of(18, 0))));
        PlannerPanel planner = new PlannerPanel(search, bookmarks, dayPlan, options);

        JTabbedPane tabs = (JTabbedPane) planner.getComponent(0);
        assertEquals(4, tabs.getTabCount());
        assertEquals("Search", tabs.getTitleAt(0));
        assertEquals("Bookmarks", tabs.getTitleAt(1));
        assertEquals("Day Plan", tabs.getTitleAt(2));
        assertEquals("Trip Options", tabs.getTitleAt(3));
        assertTrue(allText(planner).contains("Discover activities"));
        assertTrue(allText(planner).contains("Saved for later"));

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
                        "Autoschedule applied. Your Day Plan has been updated.", false)));

        assertTrue(allText(dayPlan).contains("rom"));
        assertTrue(allText(dayPlan).contains("Autoschedule applied"));

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
