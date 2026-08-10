package views;

import java.awt.FlowLayout;
import java.time.LocalTime;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * An hour, a minute and an AM/PM selector.
 *
 * <p>Shown in twelve-hour time because every other time in the application is: the Day Plan
 * says "9:00 AM – 10:00 AM", so an availability control reading "09" and "18" asked the
 * traveller to translate between two clocks inside one screen.</p>
 *
 * <p>The internal representation is untouched — {@link #getTime()} still hands back a
 * {@link LocalTime} on a 24-hour clock, and nothing downstream knows this changed.</p>
 */
public final class TimeSelectorPanel extends JPanel {

    private static final String[] MERIDIEMS = {"AM", "PM"};
    private static final int NOON = 12;

    private final JComboBox<String> hour = new JComboBox<>();
    private final JComboBox<String> minute = new JComboBox<>(
            new String[] {"00", "15", "30", "45"});
    private final JComboBox<String> meridiem = new JComboBox<>(MERIDIEMS);

    public TimeSelectorPanel(LocalTime initial) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        SwingTheme.styleComboBox(hour);
        SwingTheme.styleComboBox(minute);
        SwingTheme.styleComboBox(meridiem);
        setOpaque(false);
        // 12 first, so the list reads the way a clock face does: 12, 1, 2 ... 11.
        hour.addItem("12");
        for (int value = 1; value < NOON; value++) {
            hour.addItem(String.valueOf(value));
        }
        add(hour);
        add(new JLabel(":"));
        add(minute);
        add(meridiem);
        hour.getAccessibleContext().setAccessibleName("Hour");
        minute.getAccessibleContext().setAccessibleName("Minute");
        meridiem.getAccessibleContext().setAccessibleName("AM or PM");
        setTime(initial == null ? LocalTime.MIDNIGHT : initial);
    }

    /**
     * The selected time on a 24-hour clock.
     * @return the result of the operation
     */
    public LocalTime getTime() {
        final int twelve = hour.getSelectedIndex() == 0 ? NOON : hour.getSelectedIndex();
        final boolean afternoon = meridiem.getSelectedIndex() == 1;
        // 12 AM is midnight and 12 PM is noon; every other hour simply shifts by twelve.
        final int twentyFour;
        if (twelve == NOON) {
            twentyFour = afternoon ? NOON : 0;
        }
        else {
            twentyFour = afternoon ? twelve + NOON : twelve;
        }
        return LocalTime.of(twentyFour, minute.getSelectedIndex() * 15);
    }

    /**
     * Performs the s et ti me operation.
     * @param time the t im e value
     */
    public void setTime(LocalTime time) {
        final LocalTime value = time == null ? LocalTime.MIDNIGHT : time;
        final int twentyFour = value.getHour();
        meridiem.setSelectedIndex(twentyFour >= NOON ? 1 : 0);
        final int twelve = twentyFour % NOON;
        hour.setSelectedIndex(twelve);
        minute.setSelectedIndex(Math.min(3, value.getMinute() / 15));
    }

    /**
     * Performs the a dd ch an ge li st en er operation.
     * @param listener the l is te ne r value
     */
    public void addChangeListener(Runnable listener) {
        hour.addActionListener(event -> listener.run());
        minute.addActionListener(event -> listener.run());
        meridiem.addActionListener(event -> listener.run());
    }
}
