package closeai.adapters.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import closeai.adapters.controllers.AutoScheduleSettings;
import closeai.adapters.controllers.AutoScheduleSettingsValidator;
import closeai.domain.valueobjects.TransportationMode;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The settings dialog after the polish pass: it shows a 12-hour clock, reads one back, and
 * still refuses what it cannot understand.
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
                null, start, end, TransportationMode.WALKING));
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
    void availabilityIsPrefilledOnATwelveHourClock() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));

        List<JTextField> times = fields(dialog.getRootPane());

        assertEquals("9:00 AM", times.get(0).getText());
        assertEquals("9:00 PM", times.get(1).getText());
    }

    @Test
    void theDialogIsGroupedAndSaysWhatFormatItWants() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));

        String text = allText(dialog.getRootPane());

        assertTrue(text.contains("WHEN YOU ARE FREE"), text);
        assertTrue(text.contains("TIMES YOU ARE NOT AVAILABLE"), text);
        assertTrue(text.contains("PREFERENCES"), text);
        assertTrue(text.contains("For example 9:00 AM"),
                "the field should teach its format rather than wait to reject: " + text);
    }

    @Test
    void readingBackTheDefaultFieldsGivesTheOriginalTimes() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));

        AutoScheduleSettings settings = dialog.read();

        assertNotNull(settings, "the prefilled 12-hour text must parse");
        assertEquals(LocalTime.of(9, 0), settings.getAvailableStart());
        assertEquals(LocalTime.of(21, 0), settings.getAvailableEnd());
        assertEquals(TransportationMode.WALKING, settings.getTransportationMode());
        assertTrue(settings.isKeepCurrentOrder(), "preserve-order still defaults on");
    }

    @Test
    void typedAmPmAndTwentyFourHourTextBothParse() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));
        List<JTextField> times = fields(dialog.getRootPane());

        SwingUtilities.invokeAndWait(() -> {
            times.get(0).setText("10:30 am");
            times.get(1).setText("19:45");
        });

        AutoScheduleSettings settings = dialog.read();
        assertEquals(LocalTime.of(10, 30), settings.getAvailableStart());
        assertEquals(LocalTime.of(19, 45), settings.getAvailableEnd(),
                "the older 24-hour habit still works");
    }

    @Test
    void unparseableTextIsRefusedRatherThanGuessed() throws Exception {
        AutoScheduleSettingsDialog dialog = dialog(LocalTime.of(9, 0), LocalTime.of(21, 0));
        List<JTextField> times = fields(dialog.getRootPane());

        SwingUtilities.invokeAndWait(() -> times.get(0).setText("half past nine"));

        assertNull(dialog.read(), "a time nobody can read must not become a schedule");
    }

    /**
     * The validator's message is user-facing, so it has to speak the same clock as the
     * fields. This is the one place a 24-hour string could have survived unnoticed.
     */
    @Test
    void validationMessagesQuoteTheTripHoursOnATwelveHourClock() {
        List<String> problems = new AutoScheduleSettingsValidator().validate(
                new AutoScheduleSettings(LocalTime.of(6, 0), LocalTime.of(23, 0),
                        TransportationMode.WALKING, Collections.emptyList(), true, false),
                LocalTime.of(9, 0), LocalTime.of(21, 0));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("9:00 AM to 9:00 PM"), problems.get(0));
        assertFalse(problems.get(0).contains("09:00"), problems.get(0));
    }

    /**
     * Unavailable periods are unchanged by this pass beyond field width and labelling, so
     * the rules that mattered before still hold.
     */
    @Test
    void unavailablePeriodValidationIsUnchanged() {
        AutoScheduleSettingsValidator validator = new AutoScheduleSettingsValidator();

        List<String> inverted = validator.validate(
                new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                        TransportationMode.WALKING,
                        Collections.singletonList(new AutoScheduleSettings.Window(
                                LocalTime.of(14, 0), LocalTime.of(13, 0))), true, false),
                LocalTime.of(9, 0), LocalTime.of(21, 0));
        assertTrue(inverted.get(0).contains("must end after it starts"), inverted.toString());

        List<String> overlapping = validator.validate(
                new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                        TransportationMode.WALKING,
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
}
