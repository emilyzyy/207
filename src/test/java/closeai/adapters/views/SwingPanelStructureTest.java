package closeai.adapters.views;

import closeai.adapters.controllers.OptimizeItineraryController;
import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.adapters.viewmodels.TripOptionsState;
import closeai.adapters.viewmodels.TripOptionsViewModel;
import closeai.application.usecases.OptimizeItineraryInputBoundary;
import closeai.application.usecases.OptimizeItineraryInputData;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
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
        RecordingOptimizer optimizer = new RecordingOptimizer();
        DayPlanPanel dayPlan = new DayPlanPanel(
                dayPlanViewModel,
                new OptimizeItineraryController(optimizer, "trip-1"));
        TripOptionsPanel options = new TripOptionsPanel(new TripOptionsViewModel(
                new TripOptionsState("Toronto", LocalDate.of(2026, 7, 23),
                        LocalTime.of(9, 0), LocalTime.of(18, 0),
                        TransportationMode.WALKING)));
        PlannerPanel planner = new PlannerPanel(search, bookmarks, dayPlan, options);

        JTabbedPane tabs = (JTabbedPane) planner.getComponent(0);
        assertEquals(4, tabs.getTabCount());
        assertEquals("Search", tabs.getTitleAt(0));
        assertEquals("Bookmarks", tabs.getTitleAt(1));
        assertEquals("Day Plan", tabs.getTitleAt(2));
        assertEquals("Trip Options", tabs.getTitleAt(3));
        assertTrue(allText(planner).contains("Not wired for this milestone"));

        AbstractButton optimize = findButton(dayPlan, "Optimize Itinerary");
        assertNotNull(optimize);
        assertTrue(optimize.isEnabled());
        assertTrue(optimize.isVisible());
        assertTrue(optimize.isOpaque());
        optimize.doClick();
        assertNotNull(optimizer.input);
        assertEquals("trip-1", optimizer.input.getTripId());

        ScheduledEvent event = new ScheduledEvent(
                "event-rom", activity("rom"), LocalTime.of(10, 0),
                LocalTime.of(11, 0), EventType.ACTIVITY, "Visit");
        SwingUtilities.invokeAndWait(() -> dayPlanViewModel.setState(
                new DayPlanState("trip-1", Collections.singletonList(event),
                        "Current itinerary compacted successfully", false)));

        assertTrue(allText(dayPlan).contains("rom"));
        assertTrue(allText(dayPlan).contains("Current itinerary compacted successfully"));

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

    private static final class RecordingOptimizer implements OptimizeItineraryInputBoundary {
        private OptimizeItineraryInputData input;

        @Override
        public void execute(OptimizeItineraryInputData inputData) {
            input = inputData;
        }
    }
}
