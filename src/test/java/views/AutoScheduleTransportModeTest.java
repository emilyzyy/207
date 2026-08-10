package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;

import org.junit.jupiter.api.Test;

import entity.valueobjects.TransportationMode;
import interface_adapter.controllers.AutoScheduleSettings;

/**
 * The traveller can say how they are getting around again.
 *
 * <p>The choice disappeared when multi-day routing began estimating every leg by whichever
 * mode was quickest. That is a reasonable default and it is kept — as the default — but it
 * cannot be the only option: "fastest" can quietly plan a day around a car the traveller
 * does not have, and a walker deserves walking distances.</p>
 */
class AutoScheduleTransportModeTest {

    private static AutoScheduleSettingsDialog dialog() {
        return new AutoScheduleSettingsDialog(null, LocalTime.of(9, 0), LocalTime.of(21, 0));
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<TransportationMode> modeBox(Component root) {
        final List<Component> found = new ArrayList<>();
        collect(root, found);
        for (Component component : found) {
            if (component instanceof JComboBox
                    && "Getting around by".equals(
                            component.getAccessibleContext().getAccessibleName())) {
                return (JComboBox<TransportationMode>) component;
            }
        }
        return null;
    }

    private static void collect(Component component, List<Component> into) {
        into.add(component);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, into);
            }
        }
    }

    @Test
    void allThreeRealModesAreOfferedAlongsideFastest() {
        final AutoScheduleSettingsDialog dialog = dialog();
        final JComboBox<TransportationMode> box = modeBox(dialog.getContentPane());

        assertNotNull(box, "the dialog must offer a way to choose how you travel");
        assertEquals(4, box.getItemCount());
        final List<TransportationMode> offered = new ArrayList<>();
        for (int i = 0; i < box.getItemCount(); i++) {
            offered.add(box.getItemAt(i));
        }
        assertTrue(offered.contains(TransportationMode.WALKING), offered.toString());
        assertTrue(offered.contains(TransportationMode.DRIVING), offered.toString());
        assertTrue(offered.contains(TransportationMode.TRANSIT), offered.toString());
        assertTrue(offered.contains(TransportationMode.FASTEST), offered.toString());
        dialog.dispose();
    }

    @Test
    void fastestIsTheDefaultSoDecliningToChooseStillWorks() {
        final AutoScheduleSettingsDialog dialog = dialog();

        assertEquals(TransportationMode.FASTEST,
                modeBox(dialog.getContentPane()).getSelectedItem());
        dialog.dispose();
    }

    @Test
    void theChosenModeIsWhatTheSettingsCarry() {
        final AutoScheduleSettingsDialog dialog = dialog();
        modeBox(dialog.getContentPane()).setSelectedItem(TransportationMode.TRANSIT);

        final AutoScheduleSettings settings = dialog.read();

        assertNotNull(settings, settings == null ? "the dialog refused valid input" : "");
        assertEquals(TransportationMode.TRANSIT, settings.getTransportationMode());
        dialog.dispose();
    }

    /** Every option reads as words, not as an enum constant shouting at the user. */
    @Test
    void eachModeHasAReadableLabel() {
        assertEquals("Walking", TransportationMode.WALKING.getLabel());
        assertEquals("Driving", TransportationMode.DRIVING.getLabel());
        assertEquals("Transit", TransportationMode.TRANSIT.getLabel());
        assertEquals("Fastest available", TransportationMode.FASTEST.getLabel());
    }

    @Test
    void onlyTheRealModesCanBeRoutedFor() {
        assertEquals(3, TransportationMode.specificModes().length);
        for (TransportationMode mode : TransportationMode.specificModes()) {
            assertTrue(mode.isSpecific(), mode + " is a real means of transport");
        }
        assertTrue(!TransportationMode.FASTEST.isSpecific(),
                "fastest is a request, not a means of transport");
    }
}
