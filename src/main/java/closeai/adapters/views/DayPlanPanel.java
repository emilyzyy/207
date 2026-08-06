package closeai.adapters.views;

import closeai.adapters.controllers.AutoScheduleController;
import closeai.adapters.controllers.AutoScheduleSettings;
import closeai.adapters.viewmodels.AutoScheduleStatus;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.PreviewMetricsView;
import closeai.adapters.viewmodels.PreviewRowView;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.TransportationMode;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalTime;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * The Day Plan, and the one place Autoschedule is driven from.
 *
 * <p>A proposal is shown read-only. Evaluating a suggestion and editing a real schedule
 * are different jobs, and keeping them apart is what makes Apply and Cancel mean
 * something: until Apply succeeds, the itinerary on this screen is untouched.</p>
 *
 * <p>State arrives from a background thread, so rendering is marshalled onto the event
 * thread here rather than relying on every caller to remember.</p>
 */
public final class DayPlanPanel extends JPanel {

    private final DayPlanViewModel viewModel;
    private final AutoScheduleController autoScheduleController;
    private final JPanel eventList = new JPanel();
    private final JPanel previewArea = new JPanel();
    private final JLabel status = new JLabel();
    private final JLabel objective = new JLabel();
    private final JButton autoscheduleButton = SwingTheme.primaryButton("Autoschedule");
    private final JButton applyButton = SwingTheme.primaryButton("Apply");
    private final JButton cancelButton = new JButton("Cancel");
    private final JToggleButton whyButton = new JToggleButton("Why these times?");

    private LocalTime tripStart = LocalTime.of(9, 0);
    private LocalTime tripEnd = LocalTime.of(21, 0);
    private TransportationMode tripMode = TransportationMode.WALKING;
    private Runnable openCalendarAction = () -> { };

    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController) {
        this.viewModel = viewModel;
        this.autoScheduleController = autoScheduleController;

        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(header(), BorderLayout.NORTH);

        eventList.setLayout(new BoxLayout(eventList, BoxLayout.Y_AXIS));
        eventList.setBackground(SwingTheme.PANEL);
        previewArea.setLayout(new BoxLayout(previewArea, BoxLayout.Y_AXIS));
        previewArea.setBackground(SwingTheme.PANEL);

        JPanel centre = new JPanel();
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setBackground(SwingTheme.PANEL);
        centre.add(eventList);
        centre.add(previewArea);

        JScrollPane scroll = new JScrollPane(centre);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(scroll, BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event ->
                onEventThread(() -> render(viewModel.getState())));
    }

    /**
     * Autoschedule answers from a background thread, so state can arrive on any thread and
     * Swing may only be touched on its own. Updates already on the event thread are applied
     * straight away rather than queued, which keeps rendering predictable.
     */
    private static void onEventThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    public void setOpenCalendarAction(Runnable action) {
        openCalendarAction = action == null ? () -> { } : action;
    }

    /** Tells the panel the trip's own hours and mode, used to prefill the dialog. */
    public void setTripDefaults(LocalTime start, LocalTime end, TransportationMode mode) {
        if (start != null) {
            tripStart = start;
        }
        if (end != null) {
            tripEnd = end;
        }
        if (mode != null) {
            tripMode = mode;
        }
    }

    public DayPlanViewModel getViewModel() {
        return viewModel;
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Day Plan");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        header.add(title, BorderLayout.WEST);
        JLabel contract = new JLabel("Autoschedule reorders and retimes what you have added");
        contract.setFont(SwingTheme.SMALL);
        contract.setForeground(SwingTheme.MUTED);
        header.add(contract, BorderLayout.EAST);
        return header;
    }

    private JPanel actions() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        status.setFont(SwingTheme.SMALL);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(status);
        objective.setFont(SwingTheme.SMALL);
        objective.setForeground(SwingTheme.MUTED);
        objective.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(objective);
        wrapper.add(Box.createVerticalStrut(8));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        autoscheduleButton.setToolTipText("Suggest a better order and times for this day");
        autoscheduleButton.addActionListener(event -> openSettings());
        buttons.add(autoscheduleButton);

        applyButton.addActionListener(event -> autoScheduleController.apply());
        buttons.add(applyButton);

        cancelButton.setFont(SwingTheme.BODY);
        cancelButton.addActionListener(event -> autoScheduleController.cancel());
        buttons.add(cancelButton);

        whyButton.setFont(SwingTheme.SMALL);
        whyButton.addActionListener(event -> render(viewModel.getState()));
        buttons.add(whyButton);

        JButton calendar = new JButton("Calendar View");
        calendar.setFont(SwingTheme.BODY);
        calendar.addActionListener(event -> openCalendarAction.run());
        buttons.add(calendar);
        wrapper.add(buttons);
        return wrapper;
    }

    private void openSettings() {
        AutoScheduleSettingsDialog dialog =
                new AutoScheduleSettingsDialog(this, tripStart, tripEnd, tripMode);
        // Asking whether weather is usable means asking a forecast service, so it happens
        // off the event thread while the dialog is already on screen. The answer comes
        // back on a background thread and is applied here, on the EDT, because knowing
        // that this is Swing is the view's job rather than the controller's.
        autoScheduleController.loadWeatherOption(option ->
                SwingUtilities.invokeLater(() -> dialog.applyWeatherOption(option)));
        AutoScheduleSettings settings = dialog.showDialog();
        if (settings != null) {
            autoScheduleController.preview(settings);
        }
    }

    private void render(DayPlanState state) {
        renderItinerary(state);
        renderPreview(state);

        status.setText(state.getMessage().isEmpty()
                ? "Add activities, then choose Autoschedule." : state.getMessage());
        status.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.SUCCESS);
        objective.setText(state.getObjectiveSummary());
        objective.setVisible(!state.getObjectiveSummary().isEmpty());

        boolean previewing = state.getStatus() == AutoScheduleStatus.PREVIEW;
        boolean busy = state.getStatus() == AutoScheduleStatus.LOADING;
        autoscheduleButton.setEnabled(!state.getTripId().isEmpty() && !busy && !previewing);
        applyButton.setEnabled(previewing && !busy);
        applyButton.setVisible(previewing);
        cancelButton.setEnabled(previewing && !busy);
        cancelButton.setVisible(previewing);
        whyButton.setVisible(previewing && hasAnyReason(state));

        revalidate();
        repaint();
    }

    private boolean hasAnyReason(DayPlanState state) {
        for (PreviewRowView row : state.getPreviewRows()) {
            if (!row.getAllReasons().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void renderItinerary(DayPlanState state) {
        eventList.removeAll();
        JLabel heading = new JLabel(state.getStatus() == AutoScheduleStatus.PREVIEW
                ? "Your Day Plan now" : "Your Day Plan");
        heading.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        heading.setForeground(SwingTheme.NAVY);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventList.add(heading);
        eventList.add(Box.createVerticalStrut(6));

        if (state.getEvents().isEmpty()) {
            JLabel empty = new JLabel("No activities are currently scheduled.");
            empty.setFont(SwingTheme.BODY);
            empty.setForeground(SwingTheme.MUTED);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventList.add(empty);
            return;
        }
        for (ScheduledEvent event : state.getEvents()) {
            eventList.add(eventCard(event, state));
            eventList.add(Box.createVerticalStrut(8));
        }
    }

    private void renderPreview(DayPlanState state) {
        previewArea.removeAll();
        if (state.getStatus() != AutoScheduleStatus.PREVIEW) {
            return;
        }

        previewArea.add(Box.createVerticalStrut(12));
        JLabel heading = new JLabel("Proposed schedule (not applied yet)");
        heading.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        heading.setForeground(SwingTheme.BLUE);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewArea.add(heading);

        PreviewMetricsView metrics = state.getMetrics();
        if (metrics != null) {
            JLabel figures = new JLabel(String.format(
                    "Travel %d min to %d min  ·  waiting %d min to %d min  ·  %d of %d moved",
                    metrics.getTravelBeforeMinutes(), metrics.getTravelAfterMinutes(),
                    metrics.getIdleBeforeMinutes(), metrics.getIdleAfterMinutes(),
                    metrics.getMovedActivityCount(), metrics.getActivityCount()));
            figures.setFont(SwingTheme.SMALL);
            figures.setForeground(SwingTheme.NAVY);
            figures.setAlignmentX(Component.LEFT_ALIGNMENT);
            previewArea.add(figures);
        }

        for (String warning : state.getWarnings()) {
            previewArea.add(noticeLabel("Note: " + warning));
        }
        if (!state.getTravelQualityNote().isEmpty()) {
            previewArea.add(noticeLabel("Note: " + state.getTravelQualityNote()));
        }
        if (!state.isSearchCompletedWithinLimit()) {
            previewArea.add(noticeLabel(
                    "Note: this is the best arrangement found within the search limit."));
        }

        previewArea.add(Box.createVerticalStrut(6));
        for (PreviewRowView row : state.getPreviewRows()) {
            previewArea.add(previewCard(row));
            previewArea.add(Box.createVerticalStrut(6));
        }

        if (whyButton.isSelected()) {
            previewArea.add(whySection(state));
        }
    }

    private JLabel noticeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.SMALL);
        label.setForeground(SwingTheme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel whySection(DayPlanState state) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(Box.createVerticalStrut(8));

        JLabel heading = new JLabel("Why these times");
        heading.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        heading.setForeground(SwingTheme.NAVY);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(heading);

        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getAllReasons().isEmpty()) {
                continue;
            }
            JLabel line = new JLabel("<html><b>" + escape(row.getTitle()) + "</b> - "
                    + escape(String.join("; ", row.getAllReasons())) + "</html>");
            line.setFont(SwingTheme.SMALL);
            line.setForeground(SwingTheme.NAVY);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(line);
        }
        return section;
    }

    private JPanel previewCard(PreviewRowView row) {
        JPanel card = new JPanel(new BorderLayout(12, 4));
        SwingTheme.styleCard(card);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel time = new JLabel(row.getTimeLabel());
        time.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        time.setForeground(row.getKind() == PreviewRowView.Kind.TRAVEL
                ? SwingTheme.MUTED : SwingTheme.BLUE);
        card.add(time, BorderLayout.WEST);

        StringBuilder details = new StringBuilder("<html><b>")
                .append(escape(row.getTitle())).append("</b>");
        if (row.isLocked()) {
            details.append(" [locked]");
        } else if (row.isMoved()) {
            details.append(" [moved]");
        }
        if (!row.getReason().isEmpty()) {
            details.append("<br>").append(escape(row.getReason()));
        }
        details.append("</html>");

        JLabel label = new JLabel(details.toString());
        label.setFont(SwingTheme.BODY);
        label.setForeground(SwingTheme.NAVY);
        card.add(label, BorderLayout.CENTER);
        return card;
    }

    private JPanel eventCard(ScheduledEvent event, DayPlanState state) {
        JPanel card = new JPanel(new BorderLayout(12, 5));
        SwingTheme.styleCard(card);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel time = new JLabel(event.getStartTime() + " - " + event.getEndTime());
        time.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        time.setForeground(SwingTheme.BLUE);
        card.add(time, BorderLayout.WEST);

        String name = event.getActivity() == null
                ? (event.getNotes().isEmpty() ? event.getEventType().toString() : event.getNotes())
                : event.getActivity().getName();
        JLabel details = new JLabel("<html><b>" + escape(name) + "</b></html>");
        details.setFont(SwingTheme.BODY);
        details.setForeground(SwingTheme.NAVY);
        card.add(details, BorderLayout.CENTER);

        // Only real activities can be pinned; travel is generated, so pinning it is meaningless.
        if (event.getEventType() == EventType.ACTIVITY) {
            JCheckBox lock = new JCheckBox("Lock");
            lock.setFont(SwingTheme.SMALL);
            lock.setOpaque(false);
            lock.setSelected(state.getLockedEventIds().contains(event.getId()));
            lock.setToolTipText("Keep this activity at this exact time when autoscheduling");
            lock.getAccessibleContext().setAccessibleName("Lock " + name + " at its current time");
            lock.addActionListener(action -> autoScheduleController.toggleLock(event.getId()));
            card.add(lock, BorderLayout.EAST);
        }
        return card;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
