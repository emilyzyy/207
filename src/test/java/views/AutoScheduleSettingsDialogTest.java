package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import interface_adapter.controllers.AutoScheduleSettings;
import interface_adapter.controllers.AutoScheduleSettingsValidator;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.AbstractButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The settings dialog uses constrained 24-hour selectors for its availability window.
 *
 * <p>Dialogs allocate native windows, and Surefire shares one JVM, so every one built here
 * is disposed again — the same reason {@code AutoScheduleWeatherCheckBoxTest} does it.</p>
 */
class AutoScheduleSettingsDialogTest {

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

    private AutoScheduleSettingsDialog dialog(LocalTime start, LocalTime end) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a dialog needs a display");
        final AutoScheduleSettingsDialog[] built = new AutoScheduleSettingsDialog[1];
        SwingUtilities.invokeAndWait(() -> built[0] = new AutoScheduleSettingsDialog(
                null, start, end));
        opened.add(built[0]);
        return built[0];
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

    private static List<JTextField> fields(Component root) {
        List<JTextField> found = new ArrayList<>();
        for (Component component : all(root)) {
            if (component instanceof JTextField) {
                found.add((JTextField) component);
            }
        }
        return found;
    }

    private static List<TimeSelectorPanel> timeSelectors(Component root) {
        List<TimeSelectorPanel> found = new ArrayList<>();
        for (Component component : all(root)) {
            if (component instanceof TimeSelectorPanel) {
                found.add((TimeSelectorPanel) component);
            }
        }
        return found;
    }

    private static String allText(Component root) {
        StringBuilder text = new StringBuilder();
        for (Component component : all(root)) {
            if (component instanceof JLabel) {
                text.append(((JLabel) component).getText()).append(' ');
            }
        }
        return text.toString();
    }

    @Test
    void availabilityIsPrefilledInTheTimeSelectors() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));

        List<TimeSelectorPanel> times = timeSelectors(dialog.getRootPane());

        assertEquals(LocalTime.of(9, 0), times.get(0).getTime());
        assertEquals(LocalTime.of(21, 0), times.get(1).getTime());
    }

    @Test
    void theDialogIsGroupedWithoutObsoleteTypingExamples() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));

        String text = allText(dialog.getRootPane());

        assertTrue(text.contains("WHEN YOU ARE FREE"), text);
        assertTrue(text.contains("TIMES YOU ARE NOT AVAILABLE"), text);
        assertTrue(text.contains("PREFERENCES"), text);
        assertFalse(text.contains("For example"), text);
    }

    @Test
    void readingBackTheDefaultFieldsGivesTheOriginalTimes() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));

        AutoScheduleSettings settings = dialog.read();

        assertNotNull(settings, "the prefilled 12-hour text must parse");
        assertEquals(LocalTime.of(9, 0), settings.getAvailableStart());
        assertEquals(LocalTime.of(21, 0), settings.getAvailableEnd());
        assertTrue(settings.isKeepCurrentOrder(), "preserve-order still defaults on");
    }

    @Test
    void selectedQuarterHourTimesAreRead() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));
        List<TimeSelectorPanel> times = timeSelectors(dialog.getRootPane());

        SwingUtilities.invokeAndWait(() -> {
            times.get(0).setTime(LocalTime.of(10, 30));
            times.get(1).setTime(LocalTime.of(19, 45));
        });

        AutoScheduleSettings settings = dialog.read();
        assertEquals(LocalTime.of(10, 30), settings.getAvailableStart());
        assertEquals(LocalTime.of(19, 45), settings.getAvailableEnd(),
                "the selected military time should be preserved");
    }

    @Test
    void selectorsOnlyExposeHoursAndQuarterHours() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));
        List<TimeSelectorPanel> times = timeSelectors(dialog.getRootPane());
        times.get(0).setTime(LocalTime.of(23, 45));
        assertEquals(LocalTime.of(23, 45), times.get(0).getTime());
    }

    /**
     * The validator's message is user-facing, so it has to speak the same clock as the
     * fields. This is the one place a 24-hour string could have survived unnoticed.
     */
    @Test
    void validationMessagesQuoteTheTripHoursOnATwelveHourClock() {
        List<String> problems = new AutoScheduleSettingsValidator().validate(
                new AutoScheduleSettings(LocalTime.of(6, 0), LocalTime.of(23, 0),
                        Collections.emptyList(), true, false),
                LocalTime.of(9, 0), LocalTime.of(21, 0));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("9:00 AM to 9:00 PM"), problems.get(0));
        assertFalse(problems.get(0).contains("09:00"), problems.get(0));
    }

    // --- unavailable periods speak the same clock -------------------------------------

    private static AbstractButton addPeriodButton(Component root) {
        for (Component component : all(root)) {
            if (component instanceof AbstractButton
                    && "Add unavailable time".equals(((AbstractButton) component).getText())) {
                return (AbstractButton) component;
            }
        }
        return null;
    }

    /** Fires focusLost on every listener, which is what a real focus change does. */
    private static void loseFocus(JTextField field) {
        java.awt.event.FocusEvent event = new java.awt.event.FocusEvent(
                field, java.awt.event.FocusEvent.FOCUS_LOST);
        for (java.awt.event.FocusListener listener : field.getFocusListeners()) {
            listener.focusLost(event);
        }
    }

    /** The two text fields of the first unavailable row; availability now uses dropdowns. */
    private static List<JTextField> periodFields(AutoScheduleSettingsDialog dialog) {
        return fields(dialog.getRootPane());
    }

    private AutoScheduleSettingsDialog dialogWithOnePeriod() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));
        SwingUtilities.invokeAndWait(() -> addPeriodButton(dialog.getRootPane()).doClick());
        return dialog;
    }

    @Test
    void anUnavailablePeriodIsRenderedOnATwelveHourClock() throws Exception {
        AutoScheduleSettingsDialog dialog = dialogWithOnePeriod();

        List<JTextField> period = periodFields(dialog);

        assertEquals(2, period.size(), "one period contributes two time fields");
        assertEquals("12:00 PM", period.get(0).getText(),
                "a new period is prefilled in the same clock as everything else");
        assertEquals("1:00 PM", period.get(1).getText());
        assertTrue(allText(dialog.getRootPane()).contains("e.g. 1:00 PM"),
                "the row should show the format it wants");
    }

    @Test
    void amAndPmUnavailablePeriodInputIsUnderstood() throws Exception {
        AutoScheduleSettingsDialog dialog = dialogWithOnePeriod();
        List<JTextField> period = periodFields(dialog);

        SwingUtilities.invokeAndWait(() -> {
            period.get(0).setText("9:30 AM");
            period.get(1).setText("1:45 PM");
        });

        AutoScheduleSettings settings = dialog.read();
        assertNotNull(settings);
        assertEquals(1, settings.getUnavailableWindows().size());
        assertEquals(LocalTime.of(9, 30), settings.getUnavailableWindows().get(0).getStart());
        assertEquals(LocalTime.of(13, 45), settings.getUnavailableWindows().get(0).getEnd());
    }

    @Test
    void midnightAndNoonRoundTripThroughAnUnavailablePeriod() throws Exception {
        AutoScheduleSettingsDialog dialog = dialogWithOnePeriod();
        List<JTextField> period = periodFields(dialog);

        SwingUtilities.invokeAndWait(() -> {
            period.get(0).setText("12:00 AM");
            period.get(1).setText("12:00 PM");
        });

        AutoScheduleSettings settings = dialog.read();
        assertEquals(LocalTime.MIDNIGHT, settings.getUnavailableWindows().get(0).getStart(),
                "12:00 AM is midnight, not noon");
        assertEquals(LocalTime.NOON, settings.getUnavailableWindows().get(0).getEnd(),
                "12:00 PM is noon, not midnight");
    }

    @Test
    void typedTwentyFourHourTextIsNormalisedBackToTheTwelveHourClock() throws Exception {
        AutoScheduleSettingsDialog dialog = dialogWithOnePeriod();
        List<JTextField> period = periodFields(dialog);

        SwingUtilities.invokeAndWait(() -> {
            period.get(0).setText("13:30");
            loseFocus(period.get(0));
        });

        assertEquals("1:30 PM", period.get(0).getText(),
                "an older habit still works, and is shown back in the dialog's own clock");
        assertEquals(LocalTime.of(13, 30),
                dialog.read().getUnavailableWindows().get(0).getStart(),
                "normalising the text must not change the time it means");
    }

    @Test
    void unreadableTextIsLeftAloneRatherThanOverwritten() throws Exception {
        AutoScheduleSettingsDialog dialog = dialogWithOnePeriod();
        List<JTextField> period = periodFields(dialog);

        SwingUtilities.invokeAndWait(() -> {
            period.get(0).setText("half past one");
            loseFocus(period.get(0));
        });

        assertEquals("half past one", period.get(0).getText(),
                "silently erasing what someone typed is worse than showing it back to them");
        assertNull(dialog.read(), "and it still refuses to become a schedule");
    }

    @Test
    void everyUnavailablePeriodFieldRoundTripsToTheSameLocalTime() throws Exception {
        AutoScheduleSettingsDialog dialog = dialogWithOnePeriod();
        List<JTextField> period = periodFields(dialog);

        for (int hour = 0; hour < 24; hour++) {
            LocalTime expected = LocalTime.of(hour, 15);
            final String shown = interface_adapter.viewmodels.TimeDisplay.format(expected);
            SwingUtilities.invokeAndWait(() -> {
                period.get(0).setText(shown);
                period.get(1).setText(shown);
            });
            AutoScheduleSettings settings = dialog.read();
            assertEquals(expected, settings.getUnavailableWindows().get(0).getStart(),
                    "an unavailable period must mean the same LocalTime it displays: " + shown);
        }
    }

    /**
     * Unavailable periods are unchanged by this pass beyond presentation, so the rules that
     * mattered before still hold.
     */
    @Test
    void unavailablePeriodValidationIsUnchanged() {
        AutoScheduleSettingsValidator validator = new AutoScheduleSettingsValidator();

        List<String> inverted = validator.validate(
                new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                        Collections.singletonList(new AutoScheduleSettings.Window(
                                LocalTime.of(14, 0), LocalTime.of(13, 0))), true, false),
                LocalTime.of(9, 0), LocalTime.of(21, 0));
        assertTrue(inverted.get(0).contains("must end after it starts"), inverted.toString());

        List<String> overlapping = validator.validate(
                new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                        Arrays.asList(
                                new AutoScheduleSettings.Window(
                                        LocalTime.of(12, 0), LocalTime.of(14, 0)),
                                new AutoScheduleSettings.Window(
                                        LocalTime.of(13, 0), LocalTime.of(15, 0))),
                        true, false),
                LocalTime.of(9, 0), LocalTime.of(21, 0));
        assertTrue(overlapping.stream().anyMatch(problem -> problem.contains("overlap")),
                overlapping.toString());
    }

    /**
     * Every factor the schedule weighs is on screen, on by default, and switchable.
     *
     * <p>None of them is a hard rule, so switching any off can only change the ranking —
     * it can never make a day impossible. Weather is the one that may also be disabled by
     * circumstance, which {@code AutoScheduleWeatherCheckBoxTest} covers.</p>
     */
    @Test
    void allSixFactorsAreShownAndOnlyTheTwoRealChoicesCanBeChanged() {
        AutoScheduleSettingsDialog dialog = new AutoScheduleSettingsDialog(
                null, LocalTime.of(9, 0), LocalTime.of(21, 0));
        // With no usable forecast the weather switch is off and disabled, which is its own
        // documented state; ask for the ordinary case where it can be offered.
        dialog.applyWeatherOption(entity.valueobjects.WeatherOption.available());

        java.util.List<ToggleSwitch> switches = new java.util.ArrayList<>();
        collectSwitches(dialog.getContentPane(), switches);

        assertEquals(6, switches.size(), "six factors are weighed, so six are shown");

        int movable = 0;
        int fixedOn = 0;
        for (ToggleSwitch control : switches) {
            if (control.isEnabled()) {
                movable++;
            } else {
                assertTrue(control.isSelected(),
                        "a factor that is always applied must look on, not off");
                fixedOn++;
            }
        }
        assertEquals(6, movable, "every soft factor is the traveller's to switch off");
        assertEquals(0, fixedOn);
        for (ToggleSwitch control : switches) {
            assertTrue(control.isSelected(), "all six start on");
        }
        dialog.dispose();
    }

    private static void collectSwitches(java.awt.Component component,
                                        java.util.List<ToggleSwitch> into) {
        if (component instanceof ToggleSwitch) {
            into.add((ToggleSwitch) component);
        }
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                collectSwitches(child, into);
            }
        }
    }
}
