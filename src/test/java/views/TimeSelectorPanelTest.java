package views;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.GraphicsEnvironment;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

/**
 * The availability control reads in twelve-hour time and still hands back a 24-hour
 * {@link LocalTime}.
 *
 * <p>Midnight and noon are where a twelve-hour clock goes wrong: both are written "12", and
 * getting either backwards moves the traveller's whole day by half a rotation. They are
 * checked in both directions, along with the ordinary hours either side of them.</p>
 */
@DisabledIf("headless")
class TimeSelectorPanelTest {

    static boolean headless() {
        return GraphicsEnvironment.isHeadless();
    }

    private static TimeSelectorPanel panelFor(LocalTime initial) throws Exception {
        final TimeSelectorPanel[] built = new TimeSelectorPanel[1];
        SwingUtilities.invokeAndWait(() -> built[0] = new TimeSelectorPanel(initial));
        return built[0];
    }

    /** What the traveller reads, as one string. */
    private static String shown(TimeSelectorPanel panel) {
        List<String> parts = new ArrayList<>();
        for (java.awt.Component child : panel.getComponents()) {
            if (child instanceof JComboBox) {
                parts.add(String.valueOf(((JComboBox<?>) child).getSelectedItem()));
            }
        }
        return parts.get(0) + ":" + parts.get(1) + " " + parts.get(2);
    }

    @Test
    void midnightAndNoonAreTheOnesThatMatter() throws Exception {
        assertEquals("12:00 AM", shown(panelFor(LocalTime.of(0, 0))), "midnight is 12 AM");
        assertEquals("12:00 PM", shown(panelFor(LocalTime.of(12, 0))), "noon is 12 PM");
    }

    @Test
    void ordinaryHoursReadTheWayAClockDoes() throws Exception {
        assertEquals("1:00 AM", shown(panelFor(LocalTime.of(1, 0))));
        assertEquals("1:00 PM", shown(panelFor(LocalTime.of(13, 0))));
        assertEquals("6:00 PM", shown(panelFor(LocalTime.of(18, 0))));
        assertEquals("9:00 AM", shown(panelFor(LocalTime.of(9, 0))));
    }

    /** The minute selector offers quarter hours, so 11:59 pm is shown as the quarter below. */
    @Test
    void aTimeBetweenQuarterHoursSettlesOnTheQuarterBelowIt() throws Exception {
        assertEquals("11:45 PM", shown(panelFor(LocalTime.of(23, 59))));
    }

    @Test
    void whatIsShownIsWhatIsHandedBack() throws Exception {
        LocalTime[] times = {
            LocalTime.of(0, 0), LocalTime.of(12, 0), LocalTime.of(1, 0),
            LocalTime.of(13, 0), LocalTime.of(18, 0), LocalTime.of(9, 30),
            LocalTime.of(23, 45), LocalTime.of(11, 15),
        };
        for (LocalTime time : times) {
            assertEquals(time, panelFor(time).getTime(),
                    "the internal representation must survive the round trip for " + time);
        }
    }

    @Test
    void theQuarterHourRoundingIsTheOnlyThingLost() throws Exception {
        assertEquals(LocalTime.of(23, 45), panelFor(LocalTime.of(23, 59)).getTime());
    }
}
