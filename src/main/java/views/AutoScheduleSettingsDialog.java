package views;

import interface_adapter.controllers.AutoScheduleSettings;
import entity.valueobjects.TransportationMode;
import interface_adapter.controllers.AutoScheduleSettingsValidator;
import entity.valueobjects.WeatherOption;
import interface_adapter.viewmodels.TimeDisplay;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
 * <p>Deliberately small: when the traveller is free, any time they are not available, and
 * whether to keep the order they arranged. Everything else the schedule optimises for is
 * built in, and travel is always estimated by the fastest mode, so there is nothing else
 * to ask.</p>
 *
 * <p>Times are typed as text rather than chosen from spinners so the field can be read
 * by a screen reader and filled from the keyboard alone. Escape cancels, and the dialog
 * refuses to submit anything it can already tell is wrong.</p>
 */
public final class AutoScheduleSettingsDialog extends JDialog {

    /**
     * Shown while the forecast provider is being asked what it can offer. The checkbox
     * starts disabled and unticked, so a dialog dismissed before the answer arrives
     * simply schedules without weather rather than acting on a guess.
     */
    static final String CHECKING_WEATHER = "Checking hourly weather for this trip date...";

    private final TimeSelectorPanel availableFrom;
    private final TimeSelectorPanel availableUntil;
    private final JComboBox<TransportationMode> mode =
            new JComboBox<>(TransportationMode.values());
    private final ToggleSwitch minimizeTravel = new ToggleSwitch("Minimize travel time");
    private final ToggleSwitch minimizeGaps = new ToggleSwitch("Minimize gaps");
    private final ToggleSwitch preserveMealtimes = new ToggleSwitch("Preserve mealtimes");
    private final ToggleSwitch preferDaylight = new ToggleSwitch("Prefer daylight outdoors");
    private final ToggleSwitch keepOrder = new ToggleSwitch("Preserve plan order");
    private final ToggleSwitch considerWeather = new ToggleSwitch("Avoid bad weather");
    private final JLabel weatherNote = new JLabel(CHECKING_WEATHER);
    private final JPanel unavailableRows = new JPanel();
    private boolean resetRequested;
    private final List<TimeRangeRow> rows = new ArrayList<>();
    private final AutoScheduleSettingsValidator validator = new AutoScheduleSettingsValidator();
    private final LocalTime tripStart;
    private final LocalTime tripEnd;

    private AutoScheduleSettings result;

    public AutoScheduleSettingsDialog(Component parent, LocalTime tripStart, LocalTime tripEnd) {
        super(parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent),
                "Autoschedule", ModalityType.APPLICATION_MODAL);
        this.tripStart = tripStart;
        this.tripEnd = tripEnd;

        availableFrom = new TimeSelectorPanel(
                tripStart == null ? LocalTime.of(9, 0) : tripStart);
        availableUntil = new TimeSelectorPanel(
                tripEnd == null ? LocalTime.of(21, 0) : tripEnd);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(SwingTheme.PANEL);
        content.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        content.add(form(), BorderLayout.CENTER);
        content.add(buttons(), BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setBorder(null);
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
        form.setBackground(SwingTheme.PANEL);

        form.add(group("WHEN YOU ARE FREE"));
        JPanel hours = new JPanel(new GridBagLayout());
        hours.setOpaque(false);
        hours.setAlignmentX(Component.LEFT_ALIGNMENT);
        addField(hours, 0, "Available from", availableFrom, "");
        addField(hours, 1, "Available until", availableUntil, "");
        // Defaults to Fastest available, which asks the router for whichever real mode is
        // quickest on each leg. The three specific modes are still offered, because
        // "fastest" can quietly assume a car the traveller does not have.
        mode.setSelectedItem(TransportationMode.FASTEST);
        mode.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                    Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof TransportationMode) {
                    setText(((TransportationMode) value).getLabel());
                }
                return this;
            }
        });
        mode.getAccessibleContext().setAccessibleName("Getting around by");
        addField(hours, 2, "Getting around by", mode, "");
        hours.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                hours.getPreferredSize().height));
        form.add(hours);

        form.add(Box.createVerticalStrut(18));
        form.add(group("TIMES YOU ARE NOT AVAILABLE"));
        JLabel hint = new JLabel("Nothing is scheduled in these times, including travel.");
        hint.setFont(SwingTheme.SMALL);
        hint.setForeground(SwingTheme.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(hint);
        form.add(Box.createVerticalStrut(6));

        unavailableRows.setLayout(new BoxLayout(unavailableRows, BoxLayout.Y_AXIS));
        unavailableRows.setOpaque(false);
        unavailableRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(unavailableRows);

        JButton addRow = SwingTheme.secondaryButton("Add unavailable time");
        addRow.setFont(SwingTheme.SMALL);
        addRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        addRow.addActionListener(event -> addUnavailableRow());
        form.add(addRow);

        form.add(Box.createVerticalStrut(18));
        form.add(group("PREFERENCES"));

        // All six factors, all switchable. Turning one off removes it from the ranking
        // only -- none of them is a hard rule, so none can make a day infeasible, and the
        // traveller can see and steer every consideration rather than two of six.
        form.add(softRow(minimizeTravel, "Minimize travel time",
                "Total time spent getting between places. You still have to reach "
                        + "everywhere either way; this decides whether a shorter journey "
                        + "makes one plan better than another."));
        form.add(Box.createVerticalStrut(6));
        form.add(softRow(minimizeGaps, "Minimize gaps",
                "Waiting that is not caused by opening hours or a time you marked "
                        + "unavailable."));
        form.add(Box.createVerticalStrut(6));
        form.add(softRow(preserveMealtimes, "Preserve mealtimes",
                "Prefer a customary lunch or dinner window for places to eat."));
        form.add(Box.createVerticalStrut(6));
        form.add(softRow(preferDaylight, "Prefer daylight outdoors",
                "Prefer daylight hours for outdoor activities. Indoor places are never "
                        + "affected."));

        form.add(Box.createVerticalStrut(10));

        considerWeather.setEnabled(false);
        considerWeather.setToolTipText("Prefer to keep outdoor activities out of the worst "
                + "weather, when the forecast can tell the hours apart. Indoor activities "
                + "are never affected.");
        considerWeather.getAccessibleContext().setAccessibleDescription(CHECKING_WEATHER);
        form.add(switchRow(considerWeather, "Avoid bad weather"));

        // A visible sentence, not a greyed-out switch the user has to interpret: whenever
        // the option cannot be offered, the reason is readable text sitting next to it.
        // Colour is never the signal, and screen readers get the same words through the
        // switch's accessible description, since a disabled control may be skipped.
        weatherNote.setFont(SwingTheme.SMALL);
        weatherNote.setForeground(SwingTheme.MUTED);
        weatherNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        weatherNote.setBorder(BorderFactory.createEmptyBorder(2, 52, 0, 0));
        form.add(weatherNote);

        form.add(Box.createVerticalStrut(6));

        keepOrder.setSelected(true);
        keepOrder.setToolTipText("Prefer the order you already arranged when the days are "
                + "otherwise about as good.");
        form.add(switchRow(keepOrder, "Preserve plan order"));

        // Absorbs leftover height so the groups stay together at the top instead of being
        // spread down the dialog by the BoxLayout.
        form.add(Box.createVerticalGlue());
        return form;
    }

    /** A small capitalised group heading, matching the Day Plan's section rules. */
    private static JPanel group(String title) {
        JPanel header = SwingTheme.sectionHeader(title, "", SwingTheme.NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return header;
    }

    /** One right-aligned label, its field, and an optional format hint beneath. */
    private void addField(JPanel grid, int row, String label, Component field, String hint) {
        GridBagConstraints labelAt = new GridBagConstraints();
        labelAt.gridx = 0;
        labelAt.gridy = row;
        labelAt.anchor = GridBagConstraints.LINE_END;
        labelAt.insets = new Insets(4, 0, 4, 10);
        JLabel text = new JLabel(label);
        text.setFont(SwingTheme.BODY);
        text.setForeground(SwingTheme.NAVY);
        text.setLabelFor(field);
        grid.add(text, labelAt);

        GridBagConstraints fieldAt = new GridBagConstraints();
        fieldAt.gridx = 1;
        fieldAt.gridy = row;
        fieldAt.anchor = GridBagConstraints.LINE_START;
        fieldAt.insets = new Insets(4, 0, 4, 0);
        grid.add(field, fieldAt);

        if (!hint.isEmpty()) {
            GridBagConstraints hintAt = new GridBagConstraints();
            hintAt.gridx = 2;
            hintAt.gridy = row;
            hintAt.anchor = GridBagConstraints.LINE_START;
            hintAt.insets = new Insets(4, 10, 4, 0);
            JLabel example = new JLabel(hint);
            example.setFont(SwingTheme.SMALL);
            example.setForeground(SwingTheme.MUTED);
            grid.add(example, hintAt);
        }
    }

    /**
     * Applies the use case's answer about whether weather can be offered at all.
     *
     * <p>Called once the capability lookup finishes. Enabled means the forecast can tell
     * the hours apart, in which case the box is ticked by default and the traveller may
     * untick it. Disabled means it stays unticked and the reason is shown in words.</p>
     *
     * <p>Must be called on the event thread. Package-private callers in tests may invoke
     * it directly; the panel marshals it there itself.</p>
     */
    public void applyWeatherOption(WeatherOption option) {
        if (option == null) {
            return;
        }
        considerWeather.setEnabled(option.isAvailable());
        considerWeather.setSelected(option.isSelectedByDefault());
        String note = option.isAvailable() ? "" : option.getUnavailableReason();
        weatherNote.setText(note);
        weatherNote.setVisible(!note.isEmpty());
        considerWeather.getAccessibleContext().setAccessibleDescription(
                note.isEmpty() ? "Weather will be taken into account when arranging the day."
                        : note);
        pack();
    }

    /** The explanation currently shown beneath the weather checkbox; empty when none. */
    String weatherNoteText() {
        return weatherNote.isVisible() ? weatherNote.getText() : "";
    }

    /** Exposed so tests can assert the switch's enabled and on/off state. */
    ToggleSwitch weatherCheckBox() {
        return considerWeather;
    }

    /**
     * A switch with its label beside it, laid on one line.
     *
     * <p>The label mirrors the switch's clicks to it, so the whole row is a target the way
     * a checkbox's text is -- a 40-pixel switch alone would be a mean thing to aim for.</p>
     */
    /**
     * A soft factor: on by default, and the traveller's to switch off.
     *
     * <p>Every one of these only affects how candidate days are ranked, so switching one
     * off can never make a day impossible — it just stops that consideration breaking
     * ties. Weather is the exception, and it is built separately because it can also be
     * genuinely unavailable.</p>
     */
    private JPanel softRow(ToggleSwitch control, String text, String explanation) {
        control.setSelected(true);
        control.setToolTipText(explanation);
        control.getAccessibleContext().setAccessibleDescription(explanation);
        JPanel row = switchRow(control, text);
        row.setToolTipText(explanation);
        return row;
    }

    private JPanel switchRow(ToggleSwitch control, String text) {
        JPanel row = new JPanel();
        row.setLayout(new javax.swing.BoxLayout(row, javax.swing.BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Not greyed to match a disabled switch: only weather can be disabled, that
        // happens after this row is built, and a label dimmed at construction stayed dim
        // once the forecast arrived. The sentence under the switch already explains it.
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.BODY);
        label.setLabelFor(control);
        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (control.isEnabled()) {
                    control.doClick();
                }
            }
        });
        row.add(control);
        row.add(Box.createHorizontalStrut(12));
        row.add(label);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                control.getPreferredSize().height + 4));
        return row;
    }

    private JLabel labelFor(String text, Component field) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.BODY);
        label.setLabelFor(field);
        return label;
    }

    /**
     * Fills every control from what this day was last scheduled with.
     *
     * <p>The point is that a remembered constraint is <em>on screen</em>: the traveller can see
     * the unavailable period they entered an hour ago, change it, or press its Remove button.
     * A remembered value that shaped the answer while staying hidden would be worse than not
     * remembering at all.</p>
     */
    public void applySettings(AutoScheduleSettings settings) {
        if (settings == null) {
            return;
        }
        availableFrom.setTime(settings.getAvailableStart());
        availableUntil.setTime(settings.getAvailableEnd());
        mode.setSelectedItem(settings.getTransportationMode());
        minimizeTravel.setSelected(settings.isMinimizeTravel());
        minimizeGaps.setSelected(settings.isMinimizeGaps());
        preserveMealtimes.setSelected(settings.isPreserveMealtimes());
        preferDaylight.setSelected(settings.isPreferDaylight());
        keepOrder.setSelected(settings.isKeepCurrentOrder());
        considerWeather.setSelected(settings.isConsiderWeather());

        for (TimeRangeRow existing : new java.util.ArrayList<>(rows)) {
            rows.remove(existing);
            unavailableRows.remove(existing.panel);
        }
        for (AutoScheduleSettings.Window window : settings.getUnavailableWindows()) {
            addUnavailableRow(window.getStart(), window.getEnd());
        }
        unavailableRows.revalidate();
        pack();
    }

    private void addUnavailableRow() {
        addUnavailableRow(LocalTime.of(12, 0), LocalTime.of(13, 0));
    }

    /** Whether the traveller explicitly cleared this day's remembered settings. */
    public boolean wasResetRequested() {
        return resetRequested;
    }

    /** A fresh form: the trip's own hours, no unavailable periods, every preference on. */
    private AutoScheduleSettings defaults() {
        return new AutoScheduleSettings(
                tripStart == null ? LocalTime.of(9, 0) : tripStart,
                tripEnd == null ? LocalTime.of(21, 0) : tripEnd,
                TransportationMode.FASTEST, java.util.Collections.emptyList(),
                true, considerWeather.isEnabled(), true, true, true, true);
    }

    private void addUnavailableRow(LocalTime start, LocalTime end) {
        // A readable example beats an empty box: it shows the expected clock before the
        // traveller types, rather than correcting them afterwards.
        TimeRangeRow row = new TimeRangeRow(start, end);
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
        JPanel buttons = new JPanel(new java.awt.BorderLayout());
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        // Reset is on the left, away from the two buttons that close the dialog: it clears
        // the form rather than the plan, and confusing it with Cancel would be expensive.
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        JButton reset = SwingTheme.secondaryButton("Reset to defaults");
        reset.setToolTipText("Clear every remembered setting for this day");
        reset.addActionListener(event -> {
            resetRequested = true;
            applySettings(defaults());
        });
        left.add(reset);

        JButton cancel = SwingTheme.secondaryButton("Cancel");
        cancel.addActionListener(event -> {
            result = null;
            dispose();
        });
        JButton generate = SwingTheme.primaryButton("Generate Preview");
        generate.addActionListener(event -> submit());
        right.add(cancel);
        right.add(generate);
        buttons.add(left, java.awt.BorderLayout.WEST);
        buttons.add(right, java.awt.BorderLayout.EAST);
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
                    "Times need to look like 9:00 AM or 1:15 PM.", "Check the times",
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
        LocalTime from = availableFrom.getTime();
        LocalTime until = availableUntil.getTime();
        List<AutoScheduleSettings.Window> windows = new ArrayList<>();
        for (TimeRangeRow row : rows) {
            LocalTime start = parse(row.start.getText());
            LocalTime end = parse(row.end.getText());
            if (start == null || end == null) {
                return null;
            }
            windows.add(new AutoScheduleSettings.Window(start, end));
        }
        // A disabled checkbox is never ticked, so an unusable forecast can only ever read
        // as "do not consider weather".
        boolean weather = considerWeather.isEnabled() && considerWeather.isSelected();
        return new AutoScheduleSettings(from, until,
                (TransportationMode) mode.getSelectedItem(), windows, keepOrder.isSelected(),
                weather, minimizeTravel.isSelected(), minimizeGaps.isSelected(),
                preserveMealtimes.isSelected(), preferDaylight.isSelected());
    }

    /**
     * Reads a typed time. Delegates to {@link TimeDisplay}, which accepts 9:00 AM, 9am, 9
     * and the older 09:00, so the field reads back what it shows without making the
     * traveller learn a format.
     */
    private static LocalTime parse(String text) {
        return TimeDisplay.parse(text);
    }

    /** One start-and-end pair for a time the traveller is unavailable. */
    /**
     * One start-and-end pair for a time the traveller is unavailable.
     *
     * <p>The fields speak the same 12-hour clock as the availability inputs above: they are
     * prefilled with a readable example rather than left blank, they show the format they
     * want, and whatever is typed is normalised back to AM/PM when focus leaves. A field
     * that silently keeps "13:30" after everything else on the screen says "1:30 PM" is how
     * two clocks end up in one dialog.</p>
     *
     * <p>Only the presentation changed. Adding, removing and validating a period behave
     * exactly as before, and {@code TimeDisplay.parse} still accepts the 24-hour text an
     * older habit produces.</p>
     */
    private static final class TimeRangeRow {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        private final JTextField start = new JTextField(9);
        private final JTextField end = new JTextField(9);
        private final JButton remove = SwingTheme.secondaryButton("Remove");

        TimeRangeRow(LocalTime defaultStart, LocalTime defaultEnd) {
            panel.setOpaque(false);
            JLabel fromLabel = new JLabel("From");
            fromLabel.setFont(SwingTheme.BODY);
            fromLabel.setForeground(SwingTheme.NAVY);
            fromLabel.setLabelFor(start);
            JLabel toLabel = new JLabel("to");
            toLabel.setFont(SwingTheme.BODY);
            toLabel.setForeground(SwingTheme.NAVY);
            toLabel.setLabelFor(end);

            start.setText(TimeDisplay.format(defaultStart));
            end.setText(TimeDisplay.format(defaultEnd));
            start.getAccessibleContext().setAccessibleName(
                    "Unavailable from, for example 1:00 PM");
            end.getAccessibleContext().setAccessibleName(
                    "Unavailable until, for example 2:00 PM");
            normaliseOnFocusLoss(start);
            normaliseOnFocusLoss(end);

            JLabel hint = new JLabel("e.g. 1:00 PM");
            hint.setFont(SwingTheme.SMALL);
            hint.setForeground(SwingTheme.MUTED);

            remove.setFont(SwingTheme.SMALL);
            panel.add(fromLabel);
            panel.add(start);
            panel.add(toLabel);
            panel.add(end);
            panel.add(hint);
            panel.add(remove);
        }

        /**
         * Rewrites whatever was typed as the clock the rest of the dialog shows, so "13:30"
         * becomes "1:30 PM". Unreadable text is left exactly as typed: overwriting it would
         * destroy what the traveller entered before they could see what was wrong with it.
         */
        private static void normaliseOnFocusLoss(JTextField field) {
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent event) {
                    LocalTime parsed = TimeDisplay.parse(field.getText());
                    if (parsed != null) {
                        field.setText(TimeDisplay.format(parsed));
                    }
                }
            });
        }
    }
}
