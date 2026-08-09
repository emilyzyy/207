package trippy.adapters.views;

import trippy.adapters.controllers.AutoScheduleController;
import trippy.adapters.controllers.AutoScheduleSettings;
import trippy.adapters.controllers.ManualPlanController;
import trippy.adapters.controllers.TripDayController;
import trippy.adapters.viewmodels.ActivitySelectionViewModel;
import trippy.adapters.viewmodels.AutoScheduleStatus;
import trippy.adapters.viewmodels.DayPlanState;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.adapters.viewmodels.PreviewMetricsView;
import trippy.adapters.viewmodels.PreviewRowView;
import trippy.adapters.viewmodels.TimeDisplay;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.WeatherWarning;
import trippy.domain.valueobjects.EventType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.JTextField;
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

    /** Below this the sidebar would squeeze the schedule, so the cards go underneath. */
    static final int WIDE_LAYOUT_MINIMUM = 820;

    private final DayPlanViewModel viewModel;
    private final AutoScheduleController autoScheduleController;
    private final ManualPlanController manualPlanController;
    private final ActivitySelectionViewModel selection;
    private final JPanel eventList = new JPanel();
    private final ScheduleTimeline timeline = new ScheduleTimeline();
    private final JPanel previewArea = new JPanel();
    private final JPanel sidebarSlot = new JPanel(new BorderLayout());
    private final JLabel status = new JLabel();
    private final Spinner spinner = new Spinner();
    private final JLabel objective = new JLabel();
    private final JButton autoscheduleButton = SwingTheme.primaryButton("Autoschedule");
    private final JButton applyButton = SwingTheme.primaryButton("Apply");
    private final JButton cancelButton = SwingTheme.secondaryButton("Cancel");
    private final JToggleButton whyButton = new JToggleButton("Why this schedule?");

    private LocalTime tripStart = LocalTime.of(9, 0);
    private LocalTime tripEnd = LocalTime.of(21, 0);
    private Runnable openCalendarAction = () -> { };
    private Runnable openOptionsAction = () -> { };
    private final JButton optionsButton = SwingTheme.secondaryButton("Options");

    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController) {
        this(viewModel, autoScheduleController, null, null, null);
    }

    /**
     * Adds Alex's manual add/edit/remove actions. Optional, so the panel can still be built
     * by tests and by callers that only drive Autoschedule; where no manual controller is
     * supplied the per-event buttons are disabled rather than hidden.
     */
    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController,
                        ManualPlanController manualPlanController) {
        this(viewModel, autoScheduleController, manualPlanController, null, null);
    }

    /**
     * Adds Alex's map selection: clicking an activity card highlights it on the map. Also
     * optional, for the same reason. Autoschedule replaced the Optimize Itinerary mockup, so
     * that controller is deliberately absent — there is one production path for this panel.
     */
    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController,
                        ManualPlanController manualPlanController,
                        ActivitySelectionViewModel selection) {
        this(viewModel, autoScheduleController, manualPlanController, selection, null);
    }

    /** Kept for API compatibility; the day switcher lives in {@link DaySwitcherPanel} now. */
    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController,
                        ManualPlanController manualPlanController,
                        ActivitySelectionViewModel selection,
                        TripDayController tripDayController) {
        this.viewModel = viewModel;
        this.autoScheduleController = autoScheduleController;
        this.manualPlanController = manualPlanController;
        this.selection = selection;

        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(header(), BorderLayout.NORTH);

        eventList.setLayout(new BorderLayout(0, 8));
        eventList.setBackground(SwingTheme.BACKGROUND);
        previewArea.setLayout(new BoxLayout(previewArea, BoxLayout.Y_AXIS));
        previewArea.setBackground(SwingTheme.BACKGROUND);

        JPanel centre = new JPanel();
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setBackground(SwingTheme.BACKGROUND);
        centre.add(eventList);
        centre.add(previewArea);

        JScrollPane scroll = new JScrollPane(centre);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SwingTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        sidebarSlot.setOpaque(false);
        sidebarSlot.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        sidebarSlot.setVisible(false);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(scroll, BorderLayout.CENTER);
        body.add(sidebarSlot, BorderLayout.EAST);
        add(body, BorderLayout.CENTER);

        // Crossing the width threshold changes where the stack belongs, so a resize has to
        // re-render rather than only re-lay-out.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                render(viewModel.getState());
            }
        });
        add(actions(), BorderLayout.SOUTH);

        whyButton.setFont(SwingTheme.SMALL);
        whyButton.setFocusPainted(true);
        whyButton.setContentAreaFilled(false);
        whyButton.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        whyButton.setForeground(SwingTheme.BLUE);
        whyButton.addActionListener(event -> render(viewModel.getState()));

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event ->
                onEventThread(() -> render(viewModel.getState())));
        if (selection != null) {
            // Selection changes arrive on the event thread already, but they are marshalled
            // the same way so there is one rendering path rather than two.
            selection.addPropertyChangeListener(event ->
                    onEventThread(() -> render(viewModel.getState())));
        }
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

    public void setOpenOptionsAction(Runnable action) {
        openOptionsAction = action == null ? () -> { } : action;
        optionsButton.setEnabled(action != null);
    }

    /** Tells the panel the trip's own hours, used to prefill the dialog. */
    public void setTripDefaults(LocalTime start, LocalTime end) {
        if (start != null) {
            tripStart = start;
        }
        if (end != null) {
            tripEnd = end;
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

    /**
     * A stable action bar: what acts on the proposal sits left, what is always available
     * sits right, and nothing changes position between states.
     *
     * <p>Previously five buttons of three sizes competed in one row and Autoschedule stayed
     * on screen as a disabled blue primary while a Preview was open. Apply is the only
     * primary here, and only while there is something valid to apply.</p>
     */
    private JPanel actions() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // The spinner rides beside the status line rather than replacing it: the words
        // say what is happening, the motion says it is still happening.
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statusRow.setOpaque(false);
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        spinner.setVisible(false);
        statusRow.add(spinner);
        status.setFont(SwingTheme.BODY);
        statusRow.add(status);
        wrapper.add(statusRow);
        objective.setFont(SwingTheme.SMALL);
        objective.setForeground(SwingTheme.MUTED);
        objective.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(objective);
        wrapper.add(Box.createVerticalStrut(10));

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel primary = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        primary.setOpaque(false);
        applyButton.setToolTipText("Save this proposal to your Day Plan");
        applyButton.addActionListener(event -> autoScheduleController.apply());
        primary.add(applyButton);
        cancelButton.addActionListener(event -> autoScheduleController.cancel());
        cancelButton.setToolTipText("Discard the proposal; your Day Plan is unchanged");
        primary.add(cancelButton);
        bar.add(primary, BorderLayout.WEST);

        JPanel secondary = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        secondary.setOpaque(false);
        optionsButton.setEnabled(false);
        optionsButton.setToolTipText("Edit this trip's date and daily start/end times");
        optionsButton.addActionListener(event -> openOptionsAction.run());
        secondary.add(optionsButton);
        autoscheduleButton.setToolTipText("Suggest a better order and times for this day");
        autoscheduleButton.addActionListener(event -> openSettings());
        secondary.add(autoscheduleButton);
        JButton calendar = SwingTheme.secondaryButton("Calendar View");
        calendar.addActionListener(event -> openCalendarAction.run());
        secondary.add(calendar);
        bar.add(secondary, BorderLayout.EAST);

        bar.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                bar.getPreferredSize().height));
        wrapper.add(bar);
        return wrapper;
    }

    private void openSettings() {
        AutoScheduleSettingsDialog dialog =
                new AutoScheduleSettingsDialog(this, tripStart, tripEnd);
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
        spinner.setVisible(busy);
        if (busy) {
            status.setFont(SwingTheme.HEADING.deriveFont(15f));
            status.setForeground(SwingTheme.NAVY);
        } else {
            status.setFont(SwingTheme.BODY);
        }
        // Exactly one primary is visible in any state: Autoschedule while idle, Apply while
        // a proposal is on screen. Hiding rather than only disabling Autoschedule is what
        // stops a dead blue button sitting beside the live one during a Preview.
        autoscheduleButton.setEnabled(!state.getTripId().isEmpty() && !busy);
        autoscheduleButton.setVisible(!previewing);
        applyButton.setEnabled(previewing && !busy);
        applyButton.setVisible(previewing);
        cancelButton.setEnabled(previewing && !busy);
        cancelButton.setVisible(previewing);

        revalidate();
        repaint();
    }

    private void renderItinerary(DayPlanState state) {
        eventList.removeAll();
        eventList.add(SwingTheme.sectionHeader("YOUR DAY PLAN",
                state.getStatus() == AutoScheduleStatus.PREVIEW ? "unchanged so far" : "",
                SwingTheme.NAVY), BorderLayout.NORTH);
        timeline.setSchedule(state);
        eventList.add(timeline, BorderLayout.CENTER);
    }

    private void renderPreview(DayPlanState state) {
        previewArea.removeAll();
        if (state.getStatus() != AutoScheduleStatus.PREVIEW) {
            return;
        }

        previewArea.add(Box.createVerticalStrut(18));
        previewArea.add(SwingTheme.sectionHeader("PROPOSED SCHEDULE", "not applied yet",
                SwingTheme.BLUE));
        previewArea.add(Box.createVerticalStrut(8));

        // The improvements stack sits beside the schedule when there is room for it and
        // below when there is not. Which side it lands on is this panel's business; the
        // stack itself is identical either way, which is what lets it move to a calendar
        // view later without being rewritten.
        ScheduleImprovementsPanel improvements =
                new ScheduleImprovementsPanel(state.getImprovements());
        boolean wide = getWidth() >= WIDE_LAYOUT_MINIMUM;
        if (!wide) {
            improvements.setAlignmentX(Component.LEFT_ALIGNMENT);
            improvements.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                    improvements.getPreferredSize().height));
            previewArea.add(improvements);
            previewArea.add(Box.createVerticalStrut(10));
        }
        sidebarSlot.removeAll();
        if (wide) {
            sidebarSlot.add(improvements, BorderLayout.NORTH);
        }
        sidebarSlot.setVisible(wide);

        // Warnings get their own band. Previously they were SMALL/MUTED labels immediately
        // under the figures, which made a routing caveat look like part of the arithmetic.
        List<String> warnings = new java.util.ArrayList<>(state.getWarnings());
        if (!state.getTravelQualityNote().isEmpty()) {
            warnings.add(state.getTravelQualityNote());
        }
        if (!state.isSearchCompletedWithinLimit()) {
            warnings.add("This is the best arrangement found within the search limit.");
        }
        if (!warnings.isEmpty()) {
            JPanel band = SwingTheme.warningBand(warnings);
            band.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                    band.getPreferredSize().height));
            previewArea.add(band);
            previewArea.add(Box.createVerticalStrut(10));
        }

        for (PreviewRowView row : state.getPreviewRows()) {
            previewArea.add(previewCard(row));
            previewArea.add(Box.createVerticalStrut(6));
        }

        previewArea.add(Box.createVerticalStrut(4));
        {
            whyButton.setText((whyButton.isSelected() ? "\u25be " : "\u25b8 ")
                    + "Why this schedule?");
            whyButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            previewArea.add(whyButton);
            if (whyButton.isSelected()) {
                previewArea.add(whySection(state));
            }
        }
    }

    private JLabel noticeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.SMALL);
        label.setForeground(SwingTheme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * The reasoning, grouped by what it is about rather than listed row by row.
     *
     * <p>Per-row lines answered "what happened to this activity"; a traveller asking why the
     * day looks like this wants "what was this arranged for". Grouping answers that without
     * losing a single sentence the Presenter produced.</p>
     */
    private JPanel whySection(DayPlanState state) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(SwingTheme.BACKGROUND);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingTheme.LINE),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        PreviewMetricsView metrics = state.getMetrics();
        if (metrics != null) {
            JLabel figuresHeading = new JLabel("Before and after");
            figuresHeading.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
            figuresHeading.setForeground(SwingTheme.NAVY);
            figuresHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(figuresHeading);
            section.add(detailLine("Travel: " + metrics.getTravelBeforeMinutes()
                    + " min before, " + metrics.getTravelAfterMinutes() + " min after"));
            section.add(detailLine("Waiting: " + metrics.getIdleBeforeMinutes()
                    + " min before, " + metrics.getIdleAfterMinutes() + " min after"));
            section.add(detailLine("Activities moved: " + metrics.getMovedActivityCount()
                    + " of " + metrics.getActivityCount()));
            section.add(Box.createVerticalStrut(6));

            // Whatever got worse is stated here rather than omitted. The improvement cards
            // are positive by design, so this is the only place the full trade is visible,
            // and a comparison that only ever flatters the feature is not a comparison.
            java.util.List<String> tradeOffs = new java.util.ArrayList<>();
            if (metrics.getTravelSavedMinutes() < 0) {
                tradeOffs.add("Travel increased by " + (-metrics.getTravelSavedMinutes())
                        + " min, in exchange for the gains above");
            }
            if (metrics.getIdleSavedMinutes() < 0) {
                tradeOffs.add("Waiting increased by " + (-metrics.getIdleSavedMinutes()) + " min");
            }
            if (!tradeOffs.isEmpty()) {
                JLabel tradeHeading = new JLabel("Trade-offs");
                tradeHeading.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
                tradeHeading.setForeground(SwingTheme.NAVY);
                tradeHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
                section.add(tradeHeading);
                for (String tradeOff : tradeOffs) {
                    section.add(detailLine(tradeOff));
                }
                section.add(Box.createVerticalStrut(6));
            }
        }

        java.util.Map<String, java.util.List<String>> grouped =
                new java.util.LinkedHashMap<>();
        for (PreviewRowView row : state.getPreviewRows()) {
            for (String reason : row.getAllReasons()) {
                grouped.computeIfAbsent(categoryOf(reason),
                        key -> new java.util.ArrayList<>()).add(row.getTitle() + " — " + reason);
            }
        }

        for (java.util.Map.Entry<String, java.util.List<String>> entry : grouped.entrySet()) {
            JLabel category = new JLabel(entry.getKey());
            category.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
            category.setForeground(SwingTheme.NAVY);
            category.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(category);
            for (String line : entry.getValue()) {
                JLabel item = new JLabel("<html>&nbsp;&nbsp;" + escape(line) + "</html>");
                item.setFont(SwingTheme.SMALL);
                item.setForeground(SwingTheme.MUTED);
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                section.add(item);
            }
            section.add(Box.createVerticalStrut(6));
        }
        section.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                section.getPreferredSize().height));
        return section;
    }

    private static JLabel detailLine(String text) {
        JLabel line = new JLabel("<html>&nbsp;&nbsp;" + escape(text) + "</html>");
        line.setFont(SwingTheme.SMALL);
        line.setForeground(SwingTheme.MUTED);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    /**
     * Sorts a Presenter sentence into a heading. The wording comes from
     * {@code AutoSchedulePresenter}, so matching on it keeps the categories honest without
     * the view inventing meaning the use case did not supply.
     */
    private static String categoryOf(String reason) {
        String lower = reason.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("lock")) {
            return "Times you pinned";
        }
        if (lower.contains("mealtime") || lower.contains("meal")) {
            return "Mealtimes";
        }
        if (lower.contains("daylight") || lower.contains("dark")) {
            return "Daylight";
        }
        if (lower.contains("weather") || lower.contains("rain") || lower.contains("forecast")) {
            return "Weather";
        }
        if (lower.contains("open") || lower.contains("close")) {
            return "Opening hours";
        }
        if (lower.contains("travel") || lower.contains("journey")) {
            return "Less travel";
        }
        if (lower.contains("wait") || lower.contains("gap") || lower.contains("idle")) {
            return "Reduced waiting";
        }
        if (lower.contains("order")) {
            return "Preserved order";
        }
        return "Other considerations";
    }

    private JPanel previewCard(PreviewRowView row) {
        boolean travel = row.getKind() == PreviewRowView.Kind.TRAVEL;
        JPanel card = new JPanel(new BorderLayout(12, 4));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (travel) {
            // Generated by the scheduler, so it is deliberately quieter than anything the
            // traveller chose: no border, a tinted surface, smaller type and an indent.
            // Three signals, so this never depends on telling two greys apart.
            card.setBackground(SwingTheme.TRAVEL_SURFACE);
            card.setBorder(BorderFactory.createEmptyBorder(7, 26, 7, 14));
        } else {
            SwingTheme.styleCard(card);
        }

        JLabel time = new JLabel(row.getTimeLabel());
        time.setFont(travel ? SwingTheme.SMALL : SwingTheme.BODY.deriveFont(Font.BOLD));
        time.setForeground(travel ? SwingTheme.MUTED : SwingTheme.BLUE);
        card.add(time, BorderLayout.WEST);

        JPanel centre = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        centre.setOpaque(false);

        JLabel title = new JLabel(travel
                ? "\u21b3 " + row.getTitle() : row.getTitle());
        title.setFont(travel ? SwingTheme.SMALL : SwingTheme.BODY.deriveFont(Font.BOLD));
        title.setForeground(travel ? SwingTheme.MUTED : SwingTheme.NAVY);
        centre.add(title);

        if (row.isLocked()) {
            JLabel badge = SwingTheme.badge("Locked", SwingTheme.BLUE, SwingTheme.BLUE_SOFT);
            badge.setIcon(new LockIcon(true, 11));
            badge.setIconTextGap(4);
            centre.add(badge);
        } else if (row.isMoved()) {
            centre.add(SwingTheme.badge("Moved", SwingTheme.MUTED, SwingTheme.BACKGROUND));
        }
        card.add(centre, BorderLayout.CENTER);

        if (!travel && !row.getReason().isEmpty()) {
            JLabel reason = new JLabel(row.getReason());
            reason.setFont(SwingTheme.SMALL);
            reason.setForeground(SwingTheme.MUTED);
            card.add(reason, BorderLayout.SOUTH);
        }
        card.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                card.getPreferredSize().height));
        return card;
    }

    private JPanel eventCard(ScheduledEvent event, DayPlanState state,
                             List<WeatherWarning> hourlyWeather) {
        boolean locked = state.getLockedEventIds().contains(event.getId());
        JPanel card = new JPanel(new BorderLayout(12, 5));
        SwingTheme.styleCard(card);
        if (event.getActivity() != null) {
            card.setBackground(SwingTheme.categorySurface(
                    event.getActivity().getCategory()));
        }
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        makeSelectable(card, event);

        String name = event.getActivity() == null
                ? (event.getNotes().isEmpty() ? event.getEventType().toString() : event.getNotes())
                : event.getActivity().getName();

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);

        JLabel title = new JLabel("<html><b>" + escape(name) + "</b> &nbsp; "
                + escape(TimeDisplay.range(event.getStartTime(), event.getEndTime())) + "</html>");
        title.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        title.setForeground(SwingTheme.NAVY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(title);

        if (!event.getNotes().isEmpty()) {
            JLabel notes = new JLabel(event.getNotes());
            notes.setFont(SwingTheme.SMALL);
            notes.setForeground(SwingTheme.MUTED);
            notes.setAlignmentX(Component.LEFT_ALIGNMENT);
            details.add(notes);
        }

        // Shiyuan's per-hour forecast. Each hour is one line, shortened to a width that
        // cannot force the panel to scroll sideways, with the whole reading in a tooltip.
        if (event.getEventType() == EventType.ACTIVITY && !hourlyWeather.isEmpty()) {
            for (WeatherWarning warning : hourlyWeather) {
                String full = TimeDisplay.format(warning.getTime()) + " \u00b7 "
                        + warning.getWeatherCondition() + " \u00b7 " + warning.getMessage();
                JLabel line = new JLabel(truncate(full, 64));
                line.setFont(SwingTheme.SMALL);
                line.setForeground(SwingTheme.MUTED);
                line.setToolTipText(full);
                line.setAlignmentX(Component.LEFT_ALIGNMENT);
                details.add(line);
            }
        }
        card.add(details, BorderLayout.CENTER);

        // Travel rows are generated by the scheduler, so pinning or hand-editing one is
        // meaningless; only real activities carry controls.
        if (event.getEventType() == EventType.ACTIVITY) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            actions.setOpaque(false);
            actions.add(lockToggle(event, name, locked));

            // Alex's manual controls, disabled rather than hidden when no controller is
            // wired, so the layout does not shift between the two cases.
            JButton edit = SwingTheme.secondaryButton("Edit");
            JButton remove = SwingTheme.secondaryButton("Remove");
            edit.setEnabled(manualPlanController != null);
            remove.setEnabled(manualPlanController != null);
            edit.getAccessibleContext().setAccessibleName("Edit " + name);
            remove.getAccessibleContext().setAccessibleName("Remove " + name);
            edit.addActionListener(action -> editEvent(event));
            remove.addActionListener(action -> manualPlanController.remove(event.getId()));
            actions.add(edit);
            actions.add(remove);

            card.add(actions, BorderLayout.EAST);
        }
        card.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                card.getPreferredSize().height));
        return card;
    }

    /** Google Calendar-style day grid whose event blocks are positioned by start time. */
    private final class ScheduleTimeline extends JPanel implements Scrollable {
        private static final int HOUR_HEIGHT = 72;
        private static final int TIME_GUTTER = 68;
        private static final int EVENT_GAP = 8;
        private DayPlanState state;

        private ScheduleTimeline() {
            setLayout(null);
            setName("Day schedule timeline");
            getAccessibleContext().setAccessibleName("Day schedule timeline");
            setBackground(SwingTheme.PANEL);
            setBorder(BorderFactory.createLineBorder(SwingTheme.LINE, 1, true));
        }

        private void setSchedule(DayPlanState updatedState) {
            state = updatedState;
            removeAll();
            for (ScheduledEvent event : updatedState.getEvents()) {
                JPanel card = eventCard(event, updatedState,
                        updatedState.getHourlyWeatherFor(event));
                add(card);
                if (event.getEventType() == EventType.ACTIVITY
                        && manualPlanController != null) {
                    installDragHandling(card, event);
                }
            }
            updatePreferredSize();
            revalidate();
            repaint();
        }

        private void updatePreferredSize() {
            int minutes = Math.max(60, minutesBetween(tripStart, tripEnd));
            int height = Math.max(360, minutes * HOUR_HEIGHT / 60 + 1);
            setPreferredSize(new Dimension(520, height));
            setMinimumSize(new Dimension(0, height));
        }

        @Override
        public void doLayout() {
            if (state == null) return;
            int cardWidth = Math.max(160, getWidth() - TIME_GUTTER - EVENT_GAP * 2);
            List<ScheduledEvent> events = state.getEvents();
            for (int i = 0; i < events.size() && i < getComponentCount(); i++) {
                ScheduledEvent event = events.get(i);
                int start = signedMinutesBetween(tripStart, event.getStartTime());
                int duration = Math.max(1, minutesBetween(
                        event.getStartTime(), event.getEndTime()));
                int y = Math.max(0, start * HOUR_HEIGHT / 60 + 2);
                int height = Math.max(64, duration * HOUR_HEIGHT / 60 - 4);
                getComponent(i).setBounds(
                        TIME_GUTTER + EVENT_GAP, y, cardWidth, height);
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setFont(SwingTheme.SMALL);
            int totalMinutes = Math.max(60, minutesBetween(tripStart, tripEnd));
            for (int minute = 0; minute <= totalMinutes; minute += 60) {
                int y = minute * HOUR_HEIGHT / 60;
                LocalTime time = tripStart.plusMinutes(minute);
                String label = TimeDisplay.format(time);
                g2.setColor(SwingTheme.MUTED);
                g2.drawString(label, 8, Math.min(y + 14, getHeight() - 4));
                g2.setColor(SwingTheme.LINE);
                g2.drawLine(TIME_GUTTER, y, getWidth(), y);
            }
            g2.dispose();
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect,
                                              int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? HOUR_HEIGHT / 2 : 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect,
                                               int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL
                    ? Math.max(HOUR_HEIGHT, visibleRect.height - HOUR_HEIGHT) : 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        private void installDragHandling(JPanel card, ScheduledEvent event) {
            MouseAdapter drag = new MouseAdapter() {
                private int pointerOffset;
                private boolean moved;

                @Override
                public void mousePressed(MouseEvent mouseEvent) {
                    java.awt.Point point = SwingUtilities.convertPoint(
                            mouseEvent.getComponent(), mouseEvent.getPoint(), ScheduleTimeline.this);
                    pointerOffset = point.y - card.getY();
                    moved = false;
                }

                @Override
                public void mouseDragged(MouseEvent mouseEvent) {
                    java.awt.Point point = SwingUtilities.convertPoint(
                            mouseEvent.getComponent(), mouseEvent.getPoint(), ScheduleTimeline.this);
                    int duration = Math.max(1, minutesBetween(
                            event.getStartTime(), event.getEndTime()));
                    int total = Math.max(1, minutesBetween(tripStart, tripEnd));
                    int latestStart = Math.max(0, total - duration);
                    int maximumY = latestStart * HOUR_HEIGHT / 60;
                    int y = Math.max(0, Math.min(maximumY, point.y - pointerOffset));
                    card.setLocation(card.getX(), y);
                    moved = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent mouseEvent) {
                    if (!moved) return;
                    int duration = Math.max(1, minutesBetween(
                            event.getStartTime(), event.getEndTime()));
                    LocalTime start = draggedStartFor(
                            tripStart, tripEnd, card.getY(), HOUR_HEIGHT, duration);
                    LocalTime end = start.plusMinutes(duration);
                    manualPlanController.edit(
                            event.getId(), start.toString(), end.toString(), event.getNotes());
                }
            };
            addDragListener(card, drag);
            card.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            String tooltip = card.getToolTipText();
            card.setToolTipText((tooltip == null ? "Scheduled activity" : tooltip)
                    + "; drag to change its time");
        }

        private void addDragListener(Component component, MouseAdapter listener) {
            if (component instanceof AbstractButton) return;
            component.addMouseListener(listener);
            component.addMouseMotionListener(listener);
            if (component instanceof Container) {
                for (Component child : ((Container) component).getComponents()) {
                    addDragListener(child, listener);
                }
            }
        }
    }

    static LocalTime draggedStartFor(LocalTime dayStart, LocalTime dayEnd,
                                     int y, int hourHeight, int durationMinutes) {
        int total = Math.max(1, minutesBetween(dayStart, dayEnd));
        int latestStart = Math.max(0, total - Math.max(1, durationMinutes));
        double rawMinutes = Math.max(0, y) * 60.0 / Math.max(1, hourHeight);
        int snapped = (int) Math.round(rawMinutes / 15.0) * 15;
        return dayStart.plusMinutes(Math.max(0, Math.min(latestStart, snapped)));
    }

    private static int minutesBetween(LocalTime start, LocalTime end) {
        int minutes = (int) java.time.Duration.between(start, end).toMinutes();
        return minutes < 0 ? minutes + 24 * 60 : minutes;
    }

    private static int signedMinutesBetween(LocalTime start, LocalTime end) {
        return (int) java.time.Duration.between(start, end).toMinutes();
    }

    /**
     * The pin control: one toggle over the existing lock state and {@code toggleLock}.
     *
     * <p>A {@code JToggleButton} rather than the previous checkbox, so the padlock is the
     * control rather than a label beside a tick. It is focusable and responds to Space like
     * any button, and its accessible name and tooltip say what activating it will do, which
     * is what a screen-reader user needs before pressing it rather than after.</p>
     */
    private JToggleButton lockToggle(ScheduledEvent event, String name, boolean locked) {
        JToggleButton toggle = new JToggleButton(new LockIcon(locked, 14));
        toggle.setSelected(locked);
        toggle.setFocusPainted(true);
        toggle.setOpaque(false);
        toggle.setContentAreaFilled(false);
        toggle.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(locked ? SwingTheme.BLUE : SwingTheme.LINE),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        String at = TimeDisplay.format(event.getStartTime());
        String action = locked ? "Unlock " + name : "Lock " + name + " at " + at;
        toggle.setToolTipText(locked
                ? action + " so Autoschedule may move it"
                : action + " so Autoschedule keeps it there");
        toggle.getAccessibleContext().setAccessibleName(action);
        toggle.getAccessibleContext().setAccessibleDescription(locked
                ? name + " is pinned to " + at
                : name + " is not pinned and may be moved");
        toggle.addActionListener(action2 -> autoScheduleController.toggleLock(event.getId()));
        return toggle;
    }

    /** Keeps one forecast line from widening the panel; the full text stays in a tooltip. */
    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 1).trim() + "\u2026";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void makeSelectable(JPanel card, ScheduledEvent event) {
        if (selection == null || event.getActivity() == null) return;
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText("Show " + event.getActivity().getName() + " on the map");
        if (event.getActivity().getId().equals(selection.getSelectedActivityId())) {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SwingTheme.BLUE, 2, true),
                    BorderFactory.createEmptyBorder(11, 13, 11, 13)));
        }
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                selection.select(event.getActivity().getId());
            }
        });
    }

    private void editEvent(ScheduledEvent event) {
        JTextField start = new JTextField(TimeDisplay.format(event.getStartTime()));
        JTextField end = new JTextField(TimeDisplay.format(event.getEndTime()));
        JTextField notes = new JTextField(event.getNotes());
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(new JLabel("Start time, e.g. 9:00 AM"));
        form.add(start);
        form.add(new JLabel("End time, e.g. 10:30 AM"));
        form.add(end);
        form.add(new JLabel("Notes"));
        form.add(notes);
        int choice = JOptionPane.showConfirmDialog(
                this, form, "Edit scheduled event",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            manualPlanController.edit(
                    event.getId(), start.getText(), end.getText(), notes.getText());
        }
    }
}
