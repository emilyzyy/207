package closeai.adapters.views;

import closeai.adapters.controllers.AutoScheduleController;
import closeai.adapters.controllers.AutoScheduleSettings;
import closeai.adapters.controllers.ManualPlanController;
import closeai.adapters.controllers.TripDayController;
import closeai.adapters.viewmodels.ActivitySelectionViewModel;
import closeai.adapters.viewmodels.AutoScheduleStatus;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.PreviewMetricsView;
import closeai.adapters.viewmodels.PreviewRowView;
import closeai.adapters.viewmodels.TimeDisplay;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.EventType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
    private final TripDayController tripDayController;
    private final ActivitySelectionViewModel selection;
    private final JPanel eventList = new JPanel();
    private final JPanel previewArea = new JPanel();
    private final JPanel sidebarSlot = new JPanel(new BorderLayout());
    private final JPanel dayStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JLabel status = new JLabel();
    private final JLabel objective = new JLabel();
    private final JButton autoscheduleButton = SwingTheme.primaryButton("Autoschedule");
    private final JButton applyButton = SwingTheme.primaryButton("Apply");
    private final JButton cancelButton = SwingTheme.secondaryButton("Cancel");
    private final JToggleButton whyButton = new JToggleButton("Why this schedule?");

    private LocalTime tripStart = LocalTime.of(9, 0);
    private LocalTime tripEnd = LocalTime.of(21, 0);
    private Runnable openCalendarAction = () -> { };

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

    /** Multi-day form: also accepts the controller that switches the active day. */
    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController,
                        ManualPlanController manualPlanController,
                        ActivitySelectionViewModel selection,
                        TripDayController tripDayController) {
        this.viewModel = viewModel;
        this.autoScheduleController = autoScheduleController;
        this.manualPlanController = manualPlanController;
        this.selection = selection;
        this.tripDayController = tripDayController;

        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(header(), BorderLayout.NORTH);

        eventList.setLayout(new BoxLayout(eventList, BoxLayout.Y_AXIS));
        eventList.setBackground(SwingTheme.PANEL);
        previewArea.setLayout(new BoxLayout(previewArea, BoxLayout.Y_AXIS));
        previewArea.setBackground(SwingTheme.PANEL);

        dayStrip.setOpaque(false);
        dayStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
        dayStrip.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 40));

        JPanel centre = new JPanel();
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setBackground(SwingTheme.PANEL);
        centre.add(dayStrip);
        centre.add(eventList);
        centre.add(previewArea);

        JScrollPane scroll = new JScrollPane(centre);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(14);

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

    /** A strip of one toggle per trip day; hidden for single-day trips. */
    private JPanel daySwitcher(DayPlanState state) {
        JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        strip.setOpaque(false);
        List<java.time.LocalDate> dates = state.getTripDates();
        if (dates.size() <= 1 || tripDayController == null) {
            strip.setVisible(false);
            return strip;
        }
        for (int i = 0; i < dates.size(); i++) {
            final int index = i;
            boolean active = index == state.getActiveDayIndex();
            JToggleButton day = new JToggleButton((active ? "\u25cf " : "") + "Day "
                    + (i + 1) + " \u00b7 " + dates.get(i), active);
            day.setFont(SwingTheme.SMALL);
            day.setFocusPainted(true);
            day.setOpaque(true);
            day.setBackground(active ? SwingTheme.BLUE : SwingTheme.BACKGROUND);
            day.setForeground(active ? Color.WHITE : SwingTheme.MUTED);
            day.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(active ? SwingTheme.BLUE : SwingTheme.LINE),
                    BorderFactory.createEmptyBorder(4, 10, 4, 10)));
            day.setToolTipText("Show " + dates.get(i));
            day.addActionListener(event -> tripDayController.switchTo(index));
            strip.add(day);
        }
        return strip;
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
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SwingTheme.LINE),
                BorderFactory.createEmptyBorder(10, 0, 0, 0)));

        status.setFont(SwingTheme.SMALL);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(status);
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
        dayStrip.removeAll();
        JPanel switcher = daySwitcher(state);
        if (switcher.isVisible()) {
            dayStrip.add(switcher);
            dayStrip.add(Box.createVerticalStrut(8));
        }
        eventList.removeAll();
        eventList.add(SwingTheme.sectionHeader("YOUR DAY PLAN",
                state.getStatus() == AutoScheduleStatus.PREVIEW ? "unchanged so far" : "",
                SwingTheme.NAVY));
        eventList.add(Box.createVerticalStrut(8));

        if (state.getEvents().isEmpty()) {
            JLabel empty = new JLabel("No activities are currently scheduled.");
            empty.setFont(SwingTheme.BODY);
            empty.setForeground(SwingTheme.MUTED);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventList.add(empty);
            return;
        }
        for (ScheduledEvent event : state.getEvents()) {
            eventList.add(eventCard(event, state, state.getHourlyWeatherFor(event)));
            eventList.add(Box.createVerticalStrut(8));
        }
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
        if (locked) {
            // A pinned activity is tinted as well as badged, so the state is visible from
            // the shape of the list rather than only from the control at the end of the row.
            card.setBackground(SwingTheme.BLUE_SOFT);
        }
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        makeSelectable(card, event);

        JLabel time = new JLabel(TimeDisplay.range(event.getStartTime(), event.getEndTime()));
        time.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        time.setForeground(SwingTheme.BLUE);
        card.add(time, BorderLayout.WEST);

        String name = event.getActivity() == null
                ? (event.getNotes().isEmpty() ? event.getEventType().toString() : event.getNotes())
                : event.getActivity().getName();

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);

        JLabel title = new JLabel(name);
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
                    BorderFactory.createLineBorder(SwingTheme.BLUE, 2),
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
