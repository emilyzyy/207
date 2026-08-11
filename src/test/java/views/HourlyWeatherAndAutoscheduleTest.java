package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.WeatherWarning;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.WeatherOption;
import entity.valueobjects.WeatherSeverity;
import interface_adapter.controllers.AutoScheduleController;
import interface_adapter.controllers.TaskRunner;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import use_case.autoschedule.AutoScheduleApplyInputData;
import use_case.autoschedule.AutoScheduleInputBoundary;
import use_case.autoschedule.AutoScheduleInputData;

/**
 * The hourly forecast strip and the polished Autoschedule Day Plan, side by side.
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
        public void removeFromProposal(use_case.autoschedule.ProposalEditInputData inputData) {
            removedFromProposal.add(inputData == null ? "" : inputData.getRemoveEventId());
        }

        final java.util.List<String> removedFromProposal = new java.util.ArrayList<>();

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
        final Activity activity = new Activity(id, id, ActivityCategory.ATTRACTION,
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
        final List<Component> found = new ArrayList<>();
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
        final StringBuilder text = new StringBuilder();
        for (Component component : all(root)) {
            if (component instanceof JLabel) {
                text.append(((JLabel) component).getText()).append(' ');
            }
            else if (component instanceof AbstractButton) {
                text.append(((AbstractButton) component).getText()).append(' ');
            }
        }
        return text.toString();
    }

    private static final class Pair {
        private DayPlanPanel dayPlan;
        private HourlyForecastStrip forecast;
    }

    private Pair bothPanels(DayPlanViewModel dayPlanViewModel) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "these components need a display");
        final Pair pair = new Pair();
        SwingUtilities.invokeAndWait(() -> {
            pair.dayPlan = new DayPlanPanel(dayPlanViewModel,
                    new AutoScheduleController(new RecordingUseCase(), dayPlanViewModel,
                            TaskRunner.immediate()));
            pair.forecast = new HourlyForecastStrip(dayPlanViewModel);
        });
        return pair;
    }

    @Test
    void bothPanelsReadTheSameForecastFromTheSameViewModel() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Arrays.asList(
                hour(9, "Clear sky", WeatherSeverity.LOW),
                hour(19, "Heavy rain", WeatherSeverity.HIGH))));

        final Pair panels = bothPanels(viewModel);

        final String forecast = allText(panels.forecast);
        assertTrue(forecast.contains("9:00 AM") && forecast.contains("7:00 PM"),
                "the strip lists the hours Autoschedule is reasoning about: " + forecast);
        assertTrue(forecast.contains("☂"),
                "heavy rain shows as the rain glyph: " + forecast);
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
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Collections.singletonList(
                hour(14, "Light rain", WeatherSeverity.MEDIUM))));

        final Pair panels = bothPanels(viewModel);

        final String forecast = allText(panels.forecast);
        assertTrue(forecast.contains("2:00 PM"), forecast);
        assertTrue(allText(panels.dayPlan).contains("10:00 AM"), allText(panels.dayPlan));
    }

    @Test
    void anAutoschedulePreviewLeavesTheForecastPanelIntact() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Collections.singletonList(
                hour(9, "Clear sky", WeatherSeverity.LOW))));
        final Pair panels = bothPanels(viewModel);

        // A new state carrying the same forecast, as a preview run produces.
        SwingUtilities.invokeAndWait(() -> viewModel.setState(new DayPlanState(
                "trip-1", Arrays.asList(event("High Park", 10)), "Preview ready", false,
                Collections.singletonList(hour(9, "Clear sky", WeatherSeverity.LOW)),
                AutoScheduleStatus.PREVIEW, Collections.emptyList(), null,
                Collections.emptyList(), "", true, true, "", "",
                Collections.<String>emptySet())));

        assertTrue(allText(panels.forecast).contains("9:00 AM"),
                "an Autoschedule preview must not blank the forecast strip: "
                        + allText(panels.forecast));
    }

    @Test
    void aRefreshedForecastReachesBothPanels() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Collections.singletonList(
                hour(9, "Clear sky", WeatherSeverity.LOW))));
        final Pair panels = bothPanels(viewModel);

        SwingUtilities.invokeAndWait(() -> viewModel.setState(planWith(Arrays.asList(
                hour(9, "Clear sky", WeatherSeverity.LOW),
                hour(10, "Thunderstorm", WeatherSeverity.HIGH)))));

        assertTrue(allText(panels.forecast).contains("⚡"),
                "the thunderstorm hour arrives with its glyph: " + allText(panels.forecast));
        assertTrue(allText(panels.forecast).contains("10:00 AM"), allText(panels.forecast));
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
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(Collections.singletonList(
                hour(9, "Clear sky", WeatherSeverity.LOW))));

        final Pair panels = bothPanels(viewModel);

        boolean lockToggle = false;
        for (Component component : all(panels.dayPlan)) {
            final String name = component.getAccessibleContext() == null ? null
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
