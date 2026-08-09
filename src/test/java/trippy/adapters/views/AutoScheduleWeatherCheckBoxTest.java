package trippy.adapters.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import trippy.adapters.controllers.AutoScheduleSettings;
import trippy.application.autoschedule.WeatherOption;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The settings dialog's half of the weather contract.
 *
 * <p>"Avoid bad weather" is offered only when the forecast can distinguish one hour from
 * another. When it cannot, the box is disabled, unticked, and accompanied by a sentence
 * saying why — a sentence, because a greyed-out control with no explanation leaves the
 * traveller to guess, and because the state must be readable without relying on seeing
 * the grey at all.</p>
 */
class AutoScheduleWeatherCheckBoxTest {

    /**
     * Every dialog this class builds, so each one can be released again.
     *
     * <p>A {@code JDialog} allocates a native window as soon as it is packed, and Surefire
     * runs the whole suite in one JVM. Left undisposed, the eight dialogs these tests create
     * outlive them and keep their peers alive for every later Swing test in the same fork.
     * Disposing them is not tidiness: it is the difference between releasing eight windows
     * and leaking them into somebody else's test run.</p>
     */
    private final List<AutoScheduleSettingsDialog> opened = new ArrayList<>();

    @AfterEach
    void disposeDialogs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (AutoScheduleSettingsDialog dialog : opened) {
                dialog.dispose();
            }
            opened.clear();
        });
    }

    private AutoScheduleSettingsDialog dialog() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a dialog needs a display");
        AtomicReference<AutoScheduleSettingsDialog> built = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> built.set(new AutoScheduleSettingsDialog(
                null, LocalTime.of(9, 0), LocalTime.of(21, 0))));
        opened.add(built.get());
        return built.get();
    }

    @Test
    void usableHourlyWeatherEnablesTheCheckBoxAndTicksItByDefault() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog();

        SwingUtilities.invokeAndWait(() -> dialog.applyWeatherOption(WeatherOption.available()));

        assertTrue(dialog.weatherCheckBox().isEnabled());
        assertTrue(dialog.weatherCheckBox().isSelected(),
                "where weather can genuinely help, it should help unless declined");
        assertEquals("", dialog.weatherNoteText(),
                "nothing to explain when the option is on offer");
        assertTrue(dialog.read().isConsiderWeather());
    }

    @Test
    void theTravellerCanTurnAnAvailableWeatherPreferenceOff() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog();

        SwingUtilities.invokeAndWait(() -> {
            dialog.applyWeatherOption(WeatherOption.available());
            dialog.weatherCheckBox().setSelected(false);
        });

        assertTrue(dialog.weatherCheckBox().isEnabled(), "declining must not remove the choice");
        assertFalse(dialog.read().isConsiderWeather(),
                "an unticked box must reach the use case as a decision, not a default");
    }

    @Test
    void aWholeDayForecastDisablesAndUnticksTheCheckBox() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog();

        SwingUtilities.invokeAndWait(() -> dialog.applyWeatherOption(
                WeatherOption.unavailable(WeatherOption.NO_HOURLY_FORECAST)));

        assertFalse(dialog.weatherCheckBox().isEnabled());
        assertFalse(dialog.weatherCheckBox().isSelected());
        assertEquals(WeatherOption.NO_HOURLY_FORECAST, dialog.weatherNoteText(),
                "the reason must be readable text, never colour or absence alone");
        assertFalse(dialog.read().isConsiderWeather());
    }

    @Test
    void anUnavailableForecastDisablesAndUnticksTheCheckBox() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog();

        SwingUtilities.invokeAndWait(() -> dialog.applyWeatherOption(
                WeatherOption.unavailable(WeatherOption.NO_FORECAST)));

        assertFalse(dialog.weatherCheckBox().isEnabled());
        assertFalse(dialog.weatherCheckBox().isSelected());
        assertEquals(WeatherOption.NO_FORECAST, dialog.weatherNoteText());
        assertFalse(dialog.read().isConsiderWeather());
    }

    @Test
    void theExplanationIsAlsoAvailableToAScreenReader() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog();

        SwingUtilities.invokeAndWait(() -> dialog.applyWeatherOption(
                WeatherOption.unavailable(WeatherOption.NO_HOURLY_FORECAST)));

        // A disabled control can be skipped in focus traversal, so the reason is carried on
        // the checkbox itself as well as in the visible label beside it.
        assertEquals(WeatherOption.NO_HOURLY_FORECAST,
                dialog.weatherCheckBox().getAccessibleContext().getAccessibleDescription());
        assertEquals("Avoid bad weather",
                dialog.weatherCheckBox().getAccessibleContext().getAccessibleName());
    }

    @Test
    void theCheckBoxIsSafeBeforeTheCapabilityAnswerArrives() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog();

        // The lookup is a network call, so the dialog is usable before it returns. Until
        // then weather is off, and submitting early simply schedules without it.
        assertFalse(dialog.weatherCheckBox().isEnabled());
        assertFalse(dialog.weatherCheckBox().isSelected());
        assertEquals(AutoScheduleSettingsDialog.CHECKING_WEATHER, dialog.weatherNoteText());
        assertFalse(dialog.read().isConsiderWeather());
    }

    @Test
    void aNullAnswerLeavesTheCheckBoxUntouched() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog();

        SwingUtilities.invokeAndWait(() -> dialog.applyWeatherOption(null));

        assertFalse(dialog.weatherCheckBox().isEnabled());
        assertFalse(dialog.weatherCheckBox().isSelected());
    }

    @Test
    void theOtherSettingsAreUnaffectedByTheWeatherAnswer() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog();

        SwingUtilities.invokeAndWait(() -> dialog.applyWeatherOption(WeatherOption.available()));

        AutoScheduleSettings settings = dialog.read();
        assertEquals(LocalTime.of(9, 0), settings.getAvailableStart());
        assertEquals(LocalTime.of(21, 0), settings.getAvailableEnd());
        assertTrue(settings.isKeepCurrentOrder(), "preserve-order still defaults on");
    }
}
