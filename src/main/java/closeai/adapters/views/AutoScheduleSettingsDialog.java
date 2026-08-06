package closeai.adapters.views;

import closeai.adapters.controllers.AutoScheduleSettings;
import closeai.adapters.controllers.AutoScheduleSettingsValidator;
import closeai.domain.valueobjects.TransportationMode;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 * Asks for the few things Autoschedule cannot work out for itself.
 *
 * <p>Deliberately small: when the traveller is free, how they are getting around, any
 * time they are not available, and whether to keep the order they arranged. Everything
 * else the schedule optimises for is built in, so there is nothing else to ask.</p>
 *
 * <p>Times are typed as text rather than chosen from spinners so the field can be read
 * by a screen reader and filled from the keyboard alone. Escape cancels, and the dialog
 * refuses to submit anything it can already tell is wrong.</p>
 */
public final class AutoScheduleSettingsDialog extends JDialog {

    private final JTextField availableFrom = new JTextField(6);
    private final JTextField availableUntil = new JTextField(6);
    private final JComboBox<TransportationMode> mode =
            new JComboBox<>(TransportationMode.values());
    private final JCheckBox keepOrder = new JCheckBox("Keep my current order where possible", true);
    private final JPanel unavailableRows = new JPanel();
    private final List<TimeRangeRow> rows = new ArrayList<>();
    private final AutoScheduleSettingsValidator validator = new AutoScheduleSettingsValidator();
    private final LocalTime tripStart;
    private final LocalTime tripEnd;

    private AutoScheduleSettings result;

    public AutoScheduleSettingsDialog(Component parent, LocalTime tripStart, LocalTime tripEnd,
                                      TransportationMode tripMode) {
        super(parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent),
                "Autoschedule", ModalityType.APPLICATION_MODAL);
        this.tripStart = tripStart;
        this.tripEnd = tripEnd;

        availableFrom.setText(tripStart == null ? "09:00" : tripStart.toString());
        availableUntil.setText(tripEnd == null ? "21:00" : tripEnd.toString());
        if (tripMode != null) {
            mode.setSelectedItem(tripMode);
        }

        setLayout(new BorderLayout(0, 12));
        add(form(), BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);
        getRootPane().setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        bindEscapeToCancel();
        pack();
        setLocationRelativeTo(parent);
    }

    /** Shows the dialog and returns what was chosen, or null when cancelled. */
    public AutoScheduleSettings showDialog() {
        setVisible(true);
        return result;
    }

    private JPanel form() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JPanel hours = new JPanel(new GridLayout(3, 2, 8, 8));
        hours.add(labelFor("Available from", availableFrom));
        hours.add(availableFrom);
        hours.add(labelFor("Available until", availableUntil));
        hours.add(availableUntil);
        hours.add(labelFor("Getting around by", mode));
        hours.add(mode);
        hours.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(hours);

        form.add(Box.createVerticalStrut(12));
        JLabel unavailableTitle = new JLabel("Times I am not available");
        unavailableTitle.setFont(SwingTheme.BODY);
        unavailableTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(unavailableTitle);

        JLabel hint = new JLabel("Nothing is scheduled in these times, including travel.");
        hint.setFont(SwingTheme.SMALL);
        hint.setForeground(SwingTheme.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(hint);

        unavailableRows.setLayout(new BoxLayout(unavailableRows, BoxLayout.Y_AXIS));
        unavailableRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(unavailableRows);

        JButton addRow = new JButton("Add unavailable time");
        addRow.setFont(SwingTheme.SMALL);
        addRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        addRow.addActionListener(event -> addUnavailableRow());
        form.add(addRow);

        form.add(Box.createVerticalStrut(12));
        keepOrder.setFont(SwingTheme.BODY);
        keepOrder.setAlignmentX(Component.LEFT_ALIGNMENT);
        keepOrder.setToolTipText("Prefer the order you already arranged when the days are "
                + "otherwise about as good.");
        form.add(keepOrder);
        return form;
    }

    private JLabel labelFor(String text, Component field) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.BODY);
        label.setLabelFor(field);
        return label;
    }

    private void addUnavailableRow() {
        TimeRangeRow row = new TimeRangeRow();
        rows.add(row);
        unavailableRows.add(row.panel);
        row.remove.addActionListener(event -> {
            rows.remove(row);
            unavailableRows.remove(row.panel);
            unavailableRows.revalidate();
            unavailableRows.repaint();
            pack();
        });
        unavailableRows.revalidate();
        pack();
    }

    private JPanel buttons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> {
            result = null;
            dispose();
        });
        JButton generate = SwingTheme.primaryButton("Generate Preview");
        generate.addActionListener(event -> submit());
        buttons.add(cancel);
        buttons.add(generate);
        getRootPane().setDefaultButton(generate);
        return buttons;
    }

    private void bindEscapeToCancel() {
        getRootPane().registerKeyboardAction(event -> {
            result = null;
            dispose();
        }, KeyStroke.getKeyStroke("ESCAPE"), JPanel.WHEN_IN_FOCUSED_WINDOW);
    }

    private void submit() {
        AutoScheduleSettings settings = read();
        if (settings == null) {
            JOptionPane.showMessageDialog(this,
                    "Times need to look like 09:00.", "Check the times",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> problems = validator.validate(settings, tripStart, tripEnd);
        if (!problems.isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n", problems),
                    "Check the settings", JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = settings;
        dispose();
    }

    /** Reads the fields, or null when a time cannot be understood. */
    AutoScheduleSettings read() {
        LocalTime from = parse(availableFrom.getText());
        LocalTime until = parse(availableUntil.getText());
        if (from == null || until == null) {
            return null;
        }
        List<AutoScheduleSettings.Window> windows = new ArrayList<>();
        for (TimeRangeRow row : rows) {
            LocalTime start = parse(row.start.getText());
            LocalTime end = parse(row.end.getText());
            if (start == null || end == null) {
                return null;
            }
            windows.add(new AutoScheduleSettings.Window(start, end));
        }
        return new AutoScheduleSettings(from, until,
                (TransportationMode) mode.getSelectedItem(), windows, keepOrder.isSelected());
    }

    private static LocalTime parse(String text) {
        try {
            return LocalTime.parse(text == null ? "" : text.trim());
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    /** One start-and-end pair for a time the traveller is unavailable. */
    private static final class TimeRangeRow {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        private final JTextField start = new JTextField(6);
        private final JTextField end = new JTextField(6);
        private final JButton remove = new JButton("Remove");

        TimeRangeRow() {
            JLabel fromLabel = new JLabel("From");
            fromLabel.setLabelFor(start);
            JLabel toLabel = new JLabel("to");
            toLabel.setLabelFor(end);
            remove.setFont(SwingTheme.SMALL);
            panel.add(fromLabel);
            panel.add(start);
            panel.add(toLabel);
            panel.add(end);
            panel.add(remove);
        }
    }
}
