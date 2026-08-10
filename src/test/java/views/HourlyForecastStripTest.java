package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import entity.entities.WeatherWarning;
import entity.valueobjects.Location;
import entity.valueobjects.WeatherSeverity;
import interface_adapter.viewmodels.DashboardState;
import interface_adapter.viewmodels.DashboardViewModel;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;

/**
 * The inline forecast strip, holding the behaviour the old hourly-weather window proved:
 * every hour renders, hours are sorted, times read as a 12-hour clock, and updates flow
 * from the shared view model. The window is gone; the promises are not.
 */
final class HourlyForecastStripTest {

    @Test
    void forecastRendersEveryHourAndUpdatesFromSharedViewModel() throws Exception {
        final DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false));
        final HourlyForecastStrip strip = new HourlyForecastStrip(dayPlan);

        assertTrue(allText(strip).contains("Weather is updating"));

        final WeatherWarning afternoon = warning(
                LocalTime.of(14, 0), "Light rain", WeatherSeverity.MEDIUM,
                "21°C · 65% precipitation · 12 km/h wind");
        final WeatherWarning morning = warning(
                LocalTime.of(9, 0), "Clear sky", WeatherSeverity.LOW,
                "18°C · 5% precipitation · 4 km/h wind");
        SwingUtilities.invokeAndWait(() -> dayPlan.setState(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false,
                Arrays.asList(afternoon, morning))));

        final String forecast = allText(strip);
        assertTrue(forecast.contains("9:00 AM"), forecast);
        assertTrue(forecast.contains("2:00 PM"), forecast);
        assertTrue(forecast.indexOf("9:00 AM") < forecast.indexOf("2:00 PM"),
                "hours are sorted even when the forecast arrives out of order");
        assertTrue(forecast.contains("21°C"), forecast);
        assertTrue(forecast.contains("65%"), forecast);
        strip.disposeListeners();
    }

    @Test
    void aDisposedStripStopsListeningToTheViewModel() throws Exception {
        final DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false));
        final HourlyForecastStrip strip = new HourlyForecastStrip(dayPlan);
        strip.disposeListeners();

        SwingUtilities.invokeAndWait(() -> dayPlan.setState(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false,
                Collections.singletonList(warning(LocalTime.of(9, 0), "Clear sky",
                        WeatherSeverity.LOW, "18°C · 5% precipitation")))));

        assertTrue(allText(strip).contains("Weather is updating"),
                "after dispose the strip must not repaint from the view model");
    }

    @Test
    void conditionsMapToTheSameGlyphsTheOldWindowUsed() {
        assertEquals("⚡", HourlyForecastStrip.glyphFor("Thunderstorm"));
        assertEquals("❄", HourlyForecastStrip.glyphFor("Light snow"));
        assertEquals("☂", HourlyForecastStrip.glyphFor("Drizzle"));
        assertEquals("☁", HourlyForecastStrip.glyphFor("Overcast"));
        assertEquals("☀", HourlyForecastStrip.glyphFor("Clear sky"));
        assertEquals("☀", HourlyForecastStrip.glyphFor(null));
    }

    /** Colour repeats the glyph, so it may be soft — but wet must never look like sun. */
    @Test
    void eachConditionGetsItsOwnMutedColour() {
        final java.awt.Color sun = HourlyForecastStrip.glyphColourFor("Sunny intervals");
        final java.awt.Color rain = HourlyForecastStrip.glyphColourFor("Heavy rain");
        final java.awt.Color snow = HourlyForecastStrip.glyphColourFor("Light snow");
        final java.awt.Color cloud = HourlyForecastStrip.glyphColourFor("Overcast");

        assertTrue(sun.getRed() > sun.getBlue(), "sun leans warm");
        assertTrue(rain.getBlue() > rain.getRed(), "rain leans cool");
        assertTrue(snow.getBlue() > snow.getRed(), "snow leans cool");
        assertFalse(sun.equals(rain) || rain.equals(snow) || snow.equals(cloud),
                "conditions must not share a colour");
    }

    @Test
    void overviewUsesAnEnabledButtonForHourlyForecastWhenWeatherStateIsAvailable() {
        final DashboardViewModel dashboard = new DashboardViewModel(new DashboardState(
                "Toronto", LocalDate.of(2026, 8, 6), "Clear sky", "18°C"));
        final DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false));
        final OverviewPanel overview = new OverviewPanel(
                dashboard,
                new interface_adapter.viewmodels.SearchViewModel(
                        new interface_adapter.viewmodels.SearchState(Collections.emptyList(), "")),
                null, dayPlan, null);

        final AbstractButton preview = overview.getWeatherPreviewButton();
        assertEquals("WEATHER PREVIEW", preview.getText());
        assertTrue(preview.isEnabled());
        assertEquals(1, preview.getActionListeners().length);
    }

    /** The click is a toggle: same button opens the strip and closes it again. */
    @Test
    void theWeatherPreviewButtonTogglesTheStripInsteadOfOpeningAWindow() throws Exception {
        final DashboardViewModel dashboard = new DashboardViewModel(new DashboardState(
                "Toronto", LocalDate.of(2026, 8, 6), "Clear sky", "18°C"));
        final DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false));
        final OverviewPanel overview = new OverviewPanel(
                dashboard,
                new interface_adapter.viewmodels.SearchViewModel(
                        new interface_adapter.viewmodels.SearchState(Collections.emptyList(), "")),
                null, dayPlan, null);

        assertTrue(findStrips(overview).isEmpty(), "no strip exists before the first click");

        SwingUtilities.invokeAndWait(() -> overview.getWeatherPreviewButton().doClick());
        final java.util.List<HourlyForecastStrip> shown = findStrips(overview);
        assertEquals(1, shown.size(), "first click creates the inline strip");
        assertTrue(shown.get(0).isVisible());

        SwingUtilities.invokeAndWait(() -> overview.getWeatherPreviewButton().doClick());
        assertFalse(findStrips(overview).get(0).isVisible(), "second click hides it again");
    }

    private static java.util.List<HourlyForecastStrip> findStrips(Component root) {
        final java.util.List<HourlyForecastStrip> strips = new java.util.ArrayList<>();
        collectStrips(root, strips);
        return strips;
    }

    private static void collectStrips(Component component,
                                      java.util.List<HourlyForecastStrip> into) {
        if (component instanceof HourlyForecastStrip) {
            into.add((HourlyForecastStrip) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectStrips(child, into);
            }
        }
    }

    private WeatherWarning warning(
            LocalTime time, String condition, WeatherSeverity severity, String message) {
        return new WeatherWarning(
                new Location(43.65, -79.38, "Toronto"), time, condition, severity, message);
    }

    private String allText(Component component) {
        final StringBuilder text = new StringBuilder();
        collectText(component, text);
        return text.toString();
    }

    private void collectText(Component component, StringBuilder text) {
        if (component instanceof JLabel) {
            text.append(((JLabel) component).getText()).append(' ');
        }
        if (component instanceof AbstractButton) {
            text.append(((AbstractButton) component).getText()).append(' ');
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectText(child, text);
            }
        }
    }
}
