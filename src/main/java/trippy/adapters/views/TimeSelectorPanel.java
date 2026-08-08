package trippy.adapters.views;

import java.awt.FlowLayout;
import java.time.LocalTime;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Reusable 24-hour, quarter-hour time selector. */
public final class TimeSelectorPanel extends JPanel {
    private final JComboBox<String> hour = new JComboBox<>();
    private final JComboBox<String> minute = new JComboBox<>(
            new String[] {"00", "15", "30", "45"});

    public TimeSelectorPanel(LocalTime initial) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        SwingTheme.styleComboBox(hour);
        SwingTheme.styleComboBox(minute);
        setOpaque(false);
        for (int value = 0; value < 24; value++) {
            hour.addItem(String.format("%02d", value));
        }
        add(hour);
        add(new JLabel(":"));
        add(minute);
        setTime(initial == null ? LocalTime.MIDNIGHT : initial);
    }

    public LocalTime getTime() {
        return LocalTime.of(hour.getSelectedIndex(), minute.getSelectedIndex() * 15);
    }

    public void setTime(LocalTime time) {
        LocalTime value = time == null ? LocalTime.MIDNIGHT : time;
        hour.setSelectedIndex(value.getHour());
        minute.setSelectedIndex(Math.min(3, value.getMinute() / 15));
    }

    public void addChangeListener(Runnable listener) {
        hour.addActionListener(event -> listener.run());
        minute.addActionListener(event -> listener.run());
    }
}
