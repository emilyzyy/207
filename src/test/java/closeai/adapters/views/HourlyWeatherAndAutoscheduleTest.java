package closeai.adapters.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import closeai.adapters.controllers.AutoScheduleController;
import closeai.adapters.controllers.TaskRunner;
import closeai.adapters.viewmodels.AutoScheduleStatus;
import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.application.autoschedule.AutoScheduleApplyInputData;
import closeai.application.autoschedule.AutoScheduleInputBoundary;
import closeai.application.autoschedule.AutoScheduleInputData;
import closeai.application.autoschedule.WeatherOption;
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
import java.awt.GraphicsEnvironment;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Dennis's hourly forecast and the polished Autoschedule Day Plan, side by side.
 *
 * <p>They are separate features that happen to read the same {@link DayPlanViewModel}: his
 * popup lists the forecast hour by hour, and Autoschedule uses those same hours to decide
 * where an outdoor activity should go. Sharing state is what makes them agree, and also what
 * makes them able to break each other — a listener left behind, a state field one of them
 * forgets to carry, and the other silently stops updating.</p>
 *
 * <p>So these tests hold the pair together: both render from one view model, an Autoschedule
 * preview does not disturb the forecast, and the forecast does not disturb the preview.</p>
 */
class HourlyWeatherAndAutoscheduleTest {

    private static final class RecordingUseCase implements AutoScheduleInputBoundary {
        @Override
        public void preview(AutoScheduleInputData inputData) {
        }

        @Override
        public void apply(AutoScheduleApplyInputData inputData) {
        }

        @Override
        public WeatherOption weatherOptionFor(String tripId) {
            return WeatherOption.available();
        }
    }

    private static final Location TORONTO = new Location(43.65, -79.38, "Toronto");

    private static WeatherWarning hour(int hourOfDay, String condition,
                                       WeatherSeverity severity) {
        return new WeatherWarning(TORONTO, LocalTime.of(hourOfDay, 0), condition, severity,
                "20°C · 30% precipitation · 8 km/h wind");
    }

    private static ScheduledEvent event(String id, int startHour) {
        Activity activity = new Activity(id, id, ActivityCategory.ATTRACTION,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(9, 0), LocalTime.of(20, 0), IndoorOutdoorType.OUTDOOR, "Low");
        return new ScheduledEvent(id, activity, LocalTime.of(startHour, 0),
                LocalTime.of(startHour + 1, 0), EventType.ACTIVITY, "");
    }

    private static DayPlanState planWith(List<WeatherWarning> forecast) {
        return new DayPlanState("trip-1", Arrays.asList(event("High Park", 10)), "", false,
                forecast, AutoScheduleStatus.IDLE, Collections.emptyList(), null,
                Collections.emptyList(), "", true, true, "", "",
                Collections.<String>emptySet());
    }

    private static List<Component> all(Component root) {
        List<Component> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(Component component, List<Component> into) {
        into.add(component);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, into);
            }
        }
    }

    private static String allText(Component root) {
        StringBuilder text = new StringBuilder();
        for (Component component : all(root)) {
            if (component instanceof JLabel) {
                text.append(((JLabel) component).getText()).append(' ');
            } else if (component instanceof AbstractButton) {
                text.append(((AbstractButton) component).getText()).append(' ');
            }
        }
        return text.toString();
    }

    private static final class Pair {
        private DayPlanPanel dayPlan;
        private HourlyWeatherPanel forecast;
    }

    private Pair bothPanels(DayPlanViewModel dayPlanViewModel) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "these components need a display");
        DashboardViewModel dashboard = new DashboardViewModel(new DashboardState(
                "Toronto", LocalDate.of(2026, 8, 12), "Sunny", "Updated"));
        final Pair pair = new Pair();
        SwingUtilities.invokeAndWait(() -> {
            pair.dayPlan = new DayPlanPanel(dayPlanViewModel,
                    new AutoScheduleController(new RecordingUseCase(), dayPlanViewModel,
                            TaskRunner.immediate()));
            pair.forecast = new HourlyWeatherPanel(dashboard, dayPlanViewModel);
        });
        return pair;
    }

    @Test
    void bothPanelsReadTheSameForecastFromTheSameViewModel() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Arrays.asList(
                hour(9, "Clear sky", WeatherSeverity.LOW),
                hour(19, "Heavy rain", WeatherSeverity.HIGH))));

        Pair panels = bothPanels(viewModel);

        assertTrue(allText(panels.forecast).contains("Heavy rain"),
                "the popup lists the hours Autoschedule is reasoning about");
        assertNotNull(panels.dayPlan, "and the Day Plan is built from the same state");
        assertTrue(allText(panels.dayPlan).contains("High Park"));
    }

    /**
     * Both use a 12-hour clock. Dennis's panel formats its own hours and the Day Plan formats
     * its rows through {@code TimeDisplay}; two clocks disagreeing on the same screen is the
     * sort of thing nobody notices until a user does.
     */
    @Test
    void theTwoPanelsShowTimesOnTheSameClock() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Collections.singletonList(
                hour(14, "Light rain", WeatherSeverity.MEDIUM))));

        Pair panels = bothPanels(viewModel);

        assertTrue(allText(panels.forecast).contains("2:00 PM"), allText(panels.forecast));
        assertTrue(allText(panels.dayPlan).contains("10:00 AM"), allText(panels.dayPlan));
    }

    @Test
    void anAutoschedulePreviewLeavesTheForecastPanelIntact() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Collections.singletonList(
                hour(9, "Clear sky", WeatherSeverity.LOW))));
        Pair panels = bothPanels(viewModel);

        // A new state carrying the same forecast, as a preview run produces.
        SwingUtilities.invokeAndWait(() -> viewModel.setState(new DayPlanState(
                "trip-1", Arrays.asList(event("High Park", 10)), "Preview ready", false,
                Collections.singletonList(hour(9, "Clear sky", WeatherSeverity.LOW)),
                AutoScheduleStatus.PREVIEW, Collections.emptyList(), null,
                Collections.emptyList(), "", true, true, "", "",
                Collections.<String>emptySet())));

        assertTrue(allText(panels.forecast).contains("Clear sky"),
                "an Autoschedule preview must not blank the forecast popup");
        assertTrue(allText(panels.forecast).contains("9:00 AM"));
    }

    @Test
    void aRefreshedForecastReachesBothPanels() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Collections.singletonList(
                hour(9, "Clear sky", WeatherSeverity.LOW))));
        Pair panels = bothPanels(viewModel);

        SwingUtilities.invokeAndWait(() -> viewModel.setState(planWith(Arrays.asList(
                hour(9, "Clear sky", WeatherSeverity.LOW),
                hour(10, "Thunderstorm", WeatherSeverity.HIGH)))));

        assertTrue(allText(panels.forecast).contains("Thunderstorm"));
        assertTrue(allText(panels.forecast).contains("2 HOURLY FORECASTS"));
        assertEquals(2, viewModel.getState().getHourlyWeather().size(),
                "and the Day Plan is looking at the same two hours");
    }

    /**
     * Autoschedule's own controls survive the merge. The lock toggles, the Autoschedule
     * button and the improvements stack are the polish this branch exists for, and Dennis's
     * work touched the Overview card rather than any of them — this is the test that says so.
     */
    @Test
    void thePolishedAutoscheduleControlsAreStillPresentAlongsideTheForecast() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Collections.singletonList(
                hour(9, "Clear sky", WeatherSeverity.LOW))));

        Pair panels = bothPanels(viewModel);

        boolean lockToggle = false;
        for (Component component : all(panels.dayPlan)) {
            String name = component.getAccessibleContext() == null ? null
                    : component.getAccessibleContext().getAccessibleName();
            if (name != null && (name.startsWith("Lock ") || name.startsWith("Unlock "))) {
                lockToggle = true;
            }
        }
        assertTrue(lockToggle, "the padlock control must have survived the merge");
        assertTrue(allText(panels.dayPlan).toUpperCase().contains("AUTOSCHEDULE"),
                allText(panels.dayPlan));
    }
}
