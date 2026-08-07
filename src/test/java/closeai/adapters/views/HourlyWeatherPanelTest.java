package closeai.adapters.views;

import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.WeatherSeverity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HourlyWeatherPanelTest {

    @Test
    void forecastRendersEveryHourAndUpdatesFromSharedViewModels() throws Exception {
        DashboardViewModel dashboard = new DashboardViewModel(new DashboardState(
                "Toronto", LocalDate.of(2026, 8, 6), "Loading weather…", "Fetching forecast"));
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false));
        HourlyWeatherPanel panel = new HourlyWeatherPanel(dashboard, dayPlan);

        assertTrue(allText(panel).contains("Weather is updating"));
        assertTrue(allText(panel).contains("Toronto"));

        WeatherWarning afternoon = warning(
                LocalTime.of(14, 0), "Light rain", WeatherSeverity.MEDIUM,
                "21°C · 65% precipitation · 12 km/h wind");
        WeatherWarning morning = warning(
                LocalTime.of(9, 0), "Clear sky", WeatherSeverity.LOW,
                "18°C · 5% precipitation · 4 km/h wind");
        SwingUtilities.invokeAndWait(() -> dayPlan.setState(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false,
                Arrays.asList(afternoon, morning))));

        String forecast = allText(panel);
        assertTrue(forecast.contains("2 HOURLY FORECASTS"));
        assertTrue(forecast.contains("9:00 AM"));
        assertTrue(forecast.contains("Clear sky"));
        assertFalse(forecast.contains("14:00"));
        assertTrue(forecast.contains("2:00 PM"));
        assertTrue(forecast.contains("Light rain"));
        assertTrue(forecast.indexOf("9:00 AM") < forecast.indexOf("2:00 PM"));
        assertTrue(forecast.contains("65% precipitation"));

        SwingUtilities.invokeAndWait(() -> dashboard.setState(new DashboardState(
                "Montreal", LocalDate.of(2026, 8, 7), "Rain", "Updated")));
        assertTrue(allText(panel).contains("Montreal"));
        assertTrue(allText(panel).contains("Friday, August 7, 2026"));
        panel.disposeListeners();
    }

    @Test
    void overviewUsesAnEnabledButtonForHourlyForecastWhenWeatherStateIsAvailable() {
        DashboardViewModel dashboard = new DashboardViewModel(new DashboardState(
                "Toronto", LocalDate.of(2026, 8, 6), "Clear sky", "18°C"));
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false));
        OverviewPanel overview = new OverviewPanel(
                dashboard,
                new closeai.adapters.viewmodels.SearchViewModel(
                        new closeai.adapters.viewmodels.SearchState(Collections.emptyList(), "")),
                null, dayPlan, null);

        AbstractButton preview = overview.getWeatherPreviewButton();
        assertEquals("WEATHER PREVIEW", preview.getText());
        assertTrue(preview.isEnabled());
        assertEquals(1, preview.getActionListeners().length);
    }

    private WeatherWarning warning(
            LocalTime time, String condition, WeatherSeverity severity, String message) {
        return new WeatherWarning(
                new Location(43.65, -79.38, "Toronto"), time, condition, severity, message);
    }

    private String allText(Component component) {
        StringBuilder text = new StringBuilder();
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
