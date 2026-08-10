package views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.util.List;

import javax.swing.AbstractButton;
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
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import entity.entities.ScheduledEvent;
import entity.entities.WeatherWarning;
import entity.valueobjects.EventType;
import interface_adapter.controllers.AutoScheduleController;
import interface_adapter.controllers.AutoScheduleSettings;
import interface_adapter.controllers.ManualPlanController;
import interface_adapter.controllers.TripDayController;
import interface_adapter.viewmodels.ActivitySelectionViewModel;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.PreviewMetricsView;
import interface_adapter.viewmodels.PreviewRowView;
import interface_adapter.viewmodels.TimeDisplay;
import interface_adapter.viewmodels.TripAccessViewModel;

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

    /** The least height a journey can be drawn at and still be read. */
    static final int MINIMUM_CONNECTOR_HEIGHT = 24;

    private final DayPlanViewModel viewModel;
    private final AutoScheduleController autoScheduleController;
    private final ManualPlanController manualPlanController;
    private final ActivitySelectionViewModel selection;
    private TripAccessViewModel tripAccess;
    private final JPanel eventList = new JPanel();
    private final ScheduleTimeline timeline = new ScheduleTimeline();
    /**
     * Where the reasoning goes when the window is too narrow for two columns: above the
     * schedule, never below it. Placed after a full day of timeline it was unreachable
     * without scrolling past the whole proposal, which is the complaint this all started
     * from.
     */
    private final JPanel narrowSlot = new JPanel(new BorderLayout());
    private final JPanel sidebarSlot = new JPanel(new BorderLayout());
    /**
     * A blocking conflict, directly under the heading and above the schedule.
     *
     * <p>It used to be a line of small red text in the status row at the very bottom, where it
     * was both easy to miss and easy to mistake for the previous attempt's message — which is
     * exactly what happened: an edit was made, the same sentence was still on screen, and the
     * feature looked stuck when it had simply given the same true answer twice.</p>
     */
    private final JPanel noticeSlot = new JPanel(new BorderLayout());
    private final JLabel status = new JLabel();
    private final Spinner spinner = new Spinner();
    private final JLabel objective = new JLabel();
    private final JButton autoscheduleButton = SwingTheme.primaryButton("Autoschedule");
    private final JButton applyButton = SwingTheme.primaryButton("Apply");
    private final JButton cancelButton = SwingTheme.secondaryButton("Cancel");

    private LocalTime tripStart = LocalTime.of(9, 0);
    private LocalTime tripEnd = LocalTime.of(21, 0);
    private Runnable openCalendarAction = () -> { };
    private Runnable openOptionsAction = () -> { };
    private final JButton optionsButton = SwingTheme.secondaryButton("Options");

    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController) {
        this(viewModel, autoScheduleController, null, null, (TripAccessViewModel) null);
    }

    /**
     * Adds Alex's manual add/edit/remove actions. Optional, so the panel can still be built
     * by tests and by callers that only drive Autoschedule; where no manual controller is
     * supplied the per-event buttons are disabled rather than hidden.
     */
    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController,
                        ManualPlanController manualPlanController) {
        this(viewModel, autoScheduleController, manualPlanController, null, (TripAccessViewModel) null);
    }

    /**
     * Adds Alex's map selection: clicking an activity card highlights it on the map. Also
     * optional, for the same reason. Autoschedule replaced the Optimize Itinerary mockup, so
     * that controller is deliberately absent — there is one production path for this panel.
     */
    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController,
                        ManualPlanController manualPlanController,
                        ActivitySelectionViewModel selection) {
        this(viewModel, autoScheduleController, manualPlanController, selection, (TripAccessViewModel) null);
    }

    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController,
                        ManualPlanController manualPlanController,
                        ActivitySelectionViewModel selection,
                        TripAccessViewModel tripAccess) {
        this.viewModel = viewModel;
        this.autoScheduleController = autoScheduleController;
        this.manualPlanController = manualPlanController;
        this.selection = selection;
        this.tripAccess = tripAccess;

        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        final JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        final JPanel headerRow = header();
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(headerRow);
        noticeSlot.setOpaque(false);
        noticeSlot.setVisible(false);
        noticeSlot.setAlignmentX(Component.LEFT_ALIGNMENT);
        noticeSlot.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        top.add(noticeSlot);
        add(top, BorderLayout.NORTH);

        eventList.setLayout(new BorderLayout(0, 8));
        eventList.setBackground(SwingTheme.BACKGROUND);
        // BoxLayout lines its children up on a shared alignment axis, so one child left of
        // the others drags the whole stack off centre. Both say left.
        eventList.setAlignmentX(Component.LEFT_ALIGNMENT);
        narrowSlot.setBackground(SwingTheme.BACKGROUND);
        narrowSlot.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JPanel centre = new JPanel();
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setBackground(SwingTheme.BACKGROUND);
        centre.add(narrowSlot);
        centre.add(eventList);
        // Without this the slack goes to whichever child will take it, and the empty
        // narrow-layout slot took all of it -- pushing the schedule a third of the way down
        // a window it had plenty of room in.
        centre.add(Box.createVerticalGlue());

        final JScrollPane scroll = new JScrollPane(centre);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SwingTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        sidebarSlot.setOpaque(false);
        sidebarSlot.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        sidebarSlot.setVisible(false);
        final JPanel body = new JPanel(new BorderLayout());
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


        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event ->
                onEventThread(() -> render(viewModel.getState())));
        if (selection != null) {
            // Selection changes arrive on the event thread already, but they are marshalled
            // the same way so there is one rendering path rather than two.
            selection.addPropertyChangeListener(event ->
                    onEventThread(() -> render(viewModel.getState())));
        }
        if (tripAccess != null) {
            tripAccess.addPropertyChangeListener(event ->
                    onEventThread(() -> render(viewModel.getState())));
        }
    }

    /** Kept for API compatibility; the day switcher lives in {@link DaySwitcherPanel} now. */
    public DayPlanPanel(DayPlanViewModel viewModel, AutoScheduleController autoScheduleController,
                        ManualPlanController manualPlanController,
                        ActivitySelectionViewModel selection,
                        TripDayController tripDayController) {
        this(viewModel, autoScheduleController, manualPlanController, selection, (TripAccessViewModel) null);
    }

    private boolean canEditItinerary() {
        return tripAccess == null || tripAccess.canEditItinerary();
    }

    /**
     * Whether the per-activity Edit and Remove controls may act at all.
     *
     * <p>They may not while a Preview is open. The cards on screen then are a <em>proposal</em>,
     * but the controls behind them drive the saved-plan use cases, so pressing Remove on a
     * proposed activity wrote straight through to the repository: the Day Plan was mutated,
     * the presenter replaced the state with the saved schedule, and the Preview vanished. From
     * the outside that is indistinguishable from Apply having been pressed — the one thing
     * Preview exists to make impossible.</p>
     *
     * <p>Disabling them is the honest stop-gap. Editing the proposal itself is the behaviour
     * worth having, and it needs an application-layer operation rather than a View that quietly
     * rewrites someone's itinerary.</p>
     */
    private boolean canEditRowsNow(DayPlanState state) {
        return canEditItinerary() && state.getStatus() != AutoScheduleStatus.PREVIEW;
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

    /**
     * The one blocking notice, or nothing.
     *
     * <p>Rebuilt from the current state every render, so a retry replaces the message rather
     * than stacking a second bar, and a successful Preview removes it without anyone having
     * to remember to.</p>
     */
    private void renderNotice(DayPlanState state) {
        noticeSlot.removeAll();
        noticeSlot.setVisible(state.hasBlockingNotice());
        if (!state.hasBlockingNotice()) {
            return;
        }
        noticeSlot.add(conflictBar(state.getMessage()), BorderLayout.CENTER);
        // A conflict that appears while the traveller is scrolled halfway down the day is a
        // conflict they never see.
        SwingUtilities.invokeLater(() -> {
            final JScrollPane scroll = enclosingScrollPane();
            if (scroll != null) {
                scroll.getVerticalScrollBar().setValue(0);
            }
        });
    }

    private JScrollPane enclosingScrollPane() {
        for (Component child : getComponents()) {
            if (child instanceof Container) {
                for (Component grandchild : ((Container) child).getComponents()) {
                    if (grandchild instanceof JScrollPane) {
                        return (JScrollPane) grandchild;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A shallow inline bar: warning rule, heading, the full sentence, and one OK.
     *
     * <p>Deliberately not a dialog and not a tall card. The traveller has to be able to read
     * it and keep working in the same breath — the next thing they do is edit an activity and
     * press Autoschedule again.</p>
     */
    private JPanel conflictBar(String message) {
        final JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(SwingTheme.WARNING_SOFT);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 3, 1, 1, SwingTheme.ERROR),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        bar.getAccessibleContext().setAccessibleName("Autoschedule could not run: " + message);

        final JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);

        final JLabel heading = new JLabel("\u26a0  Couldn't generate a schedule");
        heading.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        heading.setForeground(SwingTheme.ERROR);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(heading);

        // Wrapped rather than truncated: the sentence names which activity and why, and half
        // of it is no use at all.
        // Width follows the panel and the OK button, so a long sentence grows the bar a line
        // or two instead of being cut off at a hardcoded 640 pixels.
        final JLabel detail = new JLabel("<html><div style='width:"
                + Math.max(160, getWidth() - 160) + "px'>" + escape(message) + "</div></html>");
        detail.setFont(SwingTheme.SMALL);
        detail.setForeground(SwingTheme.NAVY);
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(detail);
        bar.add(text, BorderLayout.CENTER);

        final JButton dismiss = SwingTheme.secondaryButton("OK");
        dismiss.setToolTipText("Dismiss this message; your Day Plan is unchanged");
        dismiss.getAccessibleContext().setAccessibleName("Dismiss the Autoschedule message");
        dismiss.addActionListener(event -> viewModel.setState(
                viewModel.getState().withoutNotice()));
        final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(dismiss);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel header() {
        final JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        final JLabel title = new JLabel("Day Plan");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        header.add(title, BorderLayout.WEST);
        final JLabel contract = new JLabel("Autoschedule reorders and retimes what you have added");
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
        final JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // The spinner rides beside the status line rather than replacing it: the words
        // say what is happening, the motion says it is still happening.
        final JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
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

        final JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JPanel primary = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        primary.setOpaque(false);
        applyButton.setToolTipText("Save this proposal to your Day Plan");
        applyButton.addActionListener(event -> autoScheduleController.apply());
        primary.add(applyButton);
        cancelButton.addActionListener(event -> autoScheduleController.cancel());
        cancelButton.setToolTipText("Discard the proposal; your Day Plan is unchanged");
        primary.add(cancelButton);
        bar.add(primary, BorderLayout.WEST);

        final JPanel secondary = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        secondary.setOpaque(false);
        final java.awt.Dimension actionHeight = new java.awt.Dimension(78, 38);
        optionsButton.setPreferredSize(actionHeight);
        optionsButton.setMinimumSize(actionHeight);
        optionsButton.setEnabled(false);
        optionsButton.setToolTipText("Edit this trip's date and daily start/end times");
        optionsButton.addActionListener(event -> openOptionsAction.run());
        secondary.add(optionsButton);
        autoscheduleButton.setToolTipText("Suggest a better order and times for this day");
        // Height is fixed to match the row; width follows the label. Pinning both meant
        // "Autoschedule" did not fit inside its own button and rendered as "Autosched...".
        autoscheduleButton.setPreferredSize(new java.awt.Dimension(
                autoscheduleButton.getPreferredSize().width + 24, 38));
        autoscheduleButton.setMinimumSize(autoscheduleButton.getPreferredSize());
        autoscheduleButton.addActionListener(event -> openSettings());
        secondary.add(autoscheduleButton);
        final JButton calendar = SwingTheme.secondaryButton("Calendar View");
        calendar.setPreferredSize(new java.awt.Dimension(116, 38));
        calendar.setMinimumSize(new java.awt.Dimension(116, 38));
        calendar.addActionListener(event -> openCalendarAction.run());
        secondary.add(calendar);
        bar.add(secondary, BorderLayout.EAST);

        bar.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                bar.getPreferredSize().height));
        wrapper.add(bar);
        return wrapper;
    }

    private void openSettings() {
        if (!canEditItinerary()) {
            return;
        }
        final AutoScheduleSettingsDialog dialog =
                new AutoScheduleSettingsDialog(this, tripStart, tripEnd);
        // Re-open on what this day was last scheduled with, so a remembered unavailable period
        // is on screen and removable rather than quietly shaping the next answer.
        final AutoScheduleSettings remembered = autoScheduleController.rememberedSettings();
        if (remembered != null) {
            dialog.applySettings(remembered);
        }
        // Asking whether weather is usable means asking a forecast service, so it happens
        // off the event thread while the dialog is already on screen. The answer comes
        // back on a background thread and is applied here, on the EDT, because knowing
        // that this is Swing is the view's job rather than the controller's.
        autoScheduleController.loadWeatherOption(option ->
                SwingUtilities.invokeLater(() -> dialog.applyWeatherOption(option)));
        final AutoScheduleSettings settings = dialog.showDialog();
        if (dialog.wasResetRequested() && settings == null) {
            autoScheduleController.forgetRememberedSettings();
        }
        if (settings != null) {
            autoScheduleController.preview(settings);
        }
    }

    private void render(DayPlanState state) {
        renderItinerary(state);
        renderPreview(state);

        renderNotice(state);
        // A blocking conflict has its own bar at the top and says everything there, including
        // that the day is unchanged. Leaving the status line to repeat it put the same
        // sentence on screen twice, once in green.
        status.setVisible(!state.hasBlockingNotice());
        // Wrapped rather than clipped. "Proposed schedule: 3 of 4 activities mo…" tells the
        // traveller a schedule was proposed and then hides the only number that mattered.
        final String statusText = state.getMessage().isEmpty()
                ? "Add activities, then choose Autoschedule." : state.getMessage();
        status.setText("<html><div style='width:" + Math.max(320, getWidth() - 420) + "px'>"
                + escape(statusText) + "</div></html>");
        status.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.SUCCESS);
        objective.setText(state.getObjectiveSummary());
        objective.setVisible(!state.getObjectiveSummary().isEmpty());

        final boolean previewing = state.getStatus() == AutoScheduleStatus.PREVIEW;
        final boolean busy = state.getStatus() == AutoScheduleStatus.LOADING;
        final boolean editable = canEditItinerary();
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
        autoscheduleButton.setEnabled(!state.getTripId().isEmpty() && !busy && editable);
        autoscheduleButton.setVisible(!previewing);
        if (!editable) {
            autoscheduleButton.setToolTipText("View only — you cannot change this itinerary");
        } else {
            autoscheduleButton.setToolTipText("Suggest a better order and times for this day");
        }
        applyButton.setEnabled(previewing && !busy && editable);
        applyButton.setVisible(previewing);
        cancelButton.setEnabled(previewing && !busy && editable);
        cancelButton.setVisible(previewing);

        revalidate();
        repaint();
    }

    /**
     * The proposal expressed as ordinary schedule events, so the timeline can draw it.
     *
     * <p>Preview used to be a second list underneath the real day, which meant two
     * schedules on screen and the proposal rendered in a style the rest of the app had
     * left behind. Converting the rows back into events lets the one timeline show the
     * proposal instead, in the same cards, with nothing saved until Apply.</p>
     *
     * <p>Each activity row is matched to its original event by id so the card keeps the
     * activity behind it — that is what the weather line and the drag handles read.</p>
     */
    private static List<ScheduledEvent> proposalAsEvents(DayPlanState state) {
        final java.util.Map<String, ScheduledEvent> originals = new java.util.HashMap<>();
        for (ScheduledEvent event : state.getEvents()) {
            originals.put(event.getId(), event);
        }
        final List<ScheduledEvent> proposal = new java.util.ArrayList<>();
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.TRAVEL) {
                proposal.add(new ScheduledEvent(row.getEventId(), null, row.getStart(),
                        row.getEnd(), EventType.TRAVEL, row.getTitle()));
                continue;
            }
            final ScheduledEvent original = originals.get(row.getEventId());
            final String reason = row.getReason() == null ? "" : row.getReason();
            // "Moved" was a badge on the old list. The timeline card has no badge slot, so
            // it leads the subtitle instead -- losing it would drop the one marker that
            // says which activities the schedule actually changed.
            final String note = row.isMoved() && !reason.isEmpty() ? "moved \u00b7 " + reason
                    : row.isMoved() ? "moved" : reason;
            // With no original there is no Activity to name the card, and a card titled by
            // its reason would read as an activity called "a usual mealtime". The row's own
            // title is the honest fallback.
            proposal.add(new ScheduledEvent(row.getEventId(),
                    original == null ? null : original.getActivity(),
                    row.getStart(), row.getEnd(), EventType.ACTIVITY,
                    original == null ? row.getTitle() : note));
        }
        return proposal;
    }

    private void renderItinerary(DayPlanState state) {
        eventList.removeAll();
        // The heading is the only thing telling the user which day they are looking at now
        // that the timeline itself switches to the proposal during a Preview.
        final boolean previewing = state.getStatus() == AutoScheduleStatus.PREVIEW;
        eventList.add(SwingTheme.sectionHeader(
                previewing ? "PROPOSED SCHEDULE" : "YOUR DAY PLAN",
                previewing ? "Nothing has been saved yet" : "",
                previewing ? SwingTheme.BLUE : SwingTheme.NAVY), BorderLayout.NORTH);
        timeline.setSchedule(state);
        eventList.add(timeline, BorderLayout.CENTER);
    }

    /**
     * Everything explaining the proposal, built once and placed according to the width.
     *
     * <p>There is deliberately one implementation. The Preview had grown three: a row list
     * repeating the schedule, a stack of improvement cards, and a warning band under the
     * timeline, each added at a different time and none of them aware of the others. On a
     * full day the explanation ended up below hours of empty timeline, which is where it was
     * reported from. Wide windows now put this column beside the schedule; narrow ones put it
     * above, never after.</p>
     */
    private void renderPreview(DayPlanState state) {
        narrowSlot.removeAll();
        sidebarSlot.removeAll();
        if (state.getStatus() != AutoScheduleStatus.PREVIEW) {
            sidebarSlot.setVisible(false);
            narrowSlot.setVisible(false);
            return;
        }

        final boolean wide = getWidth() >= WIDE_LAYOUT_MINIMUM;
        final JPanel column = reasoningColumn(state);
        sidebarSlot.setVisible(wide);
        // An empty BorderLayout panel still reports an unbounded maximum height, so on a wide
        // window the unused narrow slot swallowed the spare vertical space and pushed the
        // schedule a third of the way down the panel.
        narrowSlot.setVisible(!wide);
        if (wide) {
            sidebarSlot.add(column, BorderLayout.NORTH);
        } else {
            column.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                    column.getPreferredSize().height));
            // BorderLayout.NORTH, so the column takes the full width and its own height.
            // Stacked in a BoxLayout it was sized to its preferred width and centred, which
            // left it floating in the middle of the panel with dead space either side.
            narrowSlot.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
            narrowSlot.add(column, BorderLayout.NORTH);
            narrowSlot.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    column.getPreferredSize().height + 12));
        }
    }

    /** The reasoning column: what changed, what was respected, what to be careful of. */
    private JPanel reasoningColumn(DayPlanState state) {
        final JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JPanel figures = figuresCard(state.getMetrics());
        if (figures != null) {
            column.add(figures);
            column.add(Box.createVerticalStrut(10));
        }

        final ScheduleImprovementsPanel improvements =
                new ScheduleImprovementsPanel(state.getImprovements());
        improvements.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (getWidth() < WIDE_LAYOUT_MINIMUM) {
            // Side by side the tiles keep a column's width. Stacked above the schedule they
            // have the whole panel, and a 360px island under a full-width card reads as a
            // mistake.
            improvements.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                    improvements.getPreferredSize().height));
        }
        column.add(improvements);

        // Chips sit under the tiles and are visibly quieter: a constraint honoured is not the
        // same kind of claim as a saving measured in minutes, and sizing them alike would
        // make four results look like nine.
        if (!state.getConstraintChips().isEmpty()) {
            column.add(Box.createVerticalStrut(8));
            column.add(chipRow(state.getConstraintChips()));
        }
        if (!state.getTradeOff().isEmpty()) {
            column.add(Box.createVerticalStrut(8));
            column.add(tradeOffStrip(state.getTradeOff()));
        }

        // Warnings live here rather than in their own band under the schedule. A routing
        // caveat is part of how far to trust the proposal, which is what this column is for,
        // and a band below the timeline was a second place to look that competed with the
        // Apply and Cancel buttons for the same corner of the screen.
        final List<String> warnings = new java.util.ArrayList<>(state.getWarnings());
        if (!state.getTravelQualityNote().isEmpty()) {
            warnings.add(state.getTravelQualityNote());
        }
        if (!state.isSearchCompletedWithinLimit()) {
            warnings.add("This is the best arrangement found within the search limit.");
        }
        if (!warnings.isEmpty()) {
            column.add(Box.createVerticalStrut(10));
            final JPanel band = SwingTheme.warningBand(warnings);
            band.setAlignmentX(Component.LEFT_ALIGNMENT);
            band.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                    band.getPreferredSize().height));
            column.add(band);
        }

        return column;
    }

    /** The constraints this schedule worked around, wrapped into as many rows as it takes. */
    private JPanel chipRow(java.util.List<interface_adapter.viewmodels.ConstraintChipView> chips) {
        final JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 6));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (interface_adapter.viewmodels.ConstraintChipView chip : chips) {
            final JLabel label = new JLabel(chip.getMarker() + "  " + chip.getLabel());
            label.setFont(SwingTheme.SMALL);
            label.setForeground(SwingTheme.MUTED);
            label.setOpaque(true);
            label.setBackground(SwingTheme.BACKGROUND);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SwingTheme.LINE, 1, true),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)));
            label.getAccessibleContext().setAccessibleName(chip.getLabel());
            row.add(label);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /** One amber line naming a disadvantage the schedule accepted on purpose. */
    private JPanel tradeOffStrip(String sentence) {
        final JPanel strip = new JPanel(new BorderLayout());
        strip.setBackground(SwingTheme.WARNING_SOFT);
        strip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, SwingTheme.WARNING),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        strip.setAlignmentX(Component.LEFT_ALIGNMENT);
        final JLabel label = new JLabel("<html><div style='width:300px'>" + escape(sentence)
                + "</div></html>");
        label.setFont(SwingTheme.SMALL);
        label.setForeground(SwingTheme.NAVY);
        strip.add(label, BorderLayout.CENTER);
        strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, strip.getPreferredSize().height));
        return strip;
    }

    /**
     * The before-and-after figures, each named and each carrying its own difference.
     *
     * <p>Written out rather than shown as {@code 24 → 17}: an arrow between two numbers does
     * not say which direction is good, and the same arrow appeared for travel that fell and
     * waiting that rose.</p>
     */
    private JPanel figuresCard(PreviewMetricsView metrics) {
        if (metrics == null) {
            return null;
        }
        final JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(SwingTheme.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingTheme.LINE, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JLabel heading = new JLabel("BEFORE AND AFTER");
        heading.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
        heading.setForeground(SwingTheme.MUTED);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(heading);
        card.add(Box.createVerticalStrut(6));

        card.add(figureLine("Travel", metrics.getTravelBeforeMinutes(),
                metrics.getTravelAfterMinutes(), "saved", "more"));
        card.add(figureLine("Waiting", metrics.getIdleBeforeMinutes(),
                metrics.getIdleAfterMinutes(), "removed", "more"));

        final JLabel moved = new JLabel("<html><b>Activities moved</b> &nbsp;"
                + metrics.getMovedActivityCount() + " of " + metrics.getActivityCount()
                + "</html>");
        moved.setFont(SwingTheme.SMALL);
        moved.setForeground(SwingTheme.NAVY);
        moved.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(moved);
        return card;
    }

    /** One named figure: before, proposed, and what the difference means in words. */
    private JLabel figureLine(String name, int before, int after,
                              String betterWord, String worseWord) {
        final int difference = before - after;
        final String change;
        if (difference > 0) {
            change = "<font color='#1A7F53'>" + difference + " min " + betterWord + "</font>";
        } else if (difference < 0) {
            change = "<font color='#925E06'>" + (-difference) + " min " + worseWord + "</font>";
        } else {
            change = "<font color='#5B6A7B'>unchanged</font>";
        }
        final JLabel line = new JLabel("<html><b>" + escape(name) + "</b> &nbsp; Before "
                + before + " min &nbsp;·&nbsp; Proposed " + after + " min &nbsp;·&nbsp; "
                + change + "</html>");
        line.setFont(SwingTheme.SMALL);
        line.setForeground(SwingTheme.NAVY);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return line;
    }

    private JLabel noticeLabel(String text) {
        final JLabel label = new JLabel(text);
        label.setFont(SwingTheme.SMALL);
        label.setForeground(SwingTheme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

        private JPanel previewCard(PreviewRowView row) {
        final boolean travel = row.getKind() == PreviewRowView.Kind.TRAVEL;
        final JPanel card = new JPanel(new BorderLayout(12, 4));
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

        final JLabel time = new JLabel(row.getTimeLabel());
        time.setFont(travel ? SwingTheme.SMALL : SwingTheme.BODY.deriveFont(Font.BOLD));
        time.setForeground(travel ? SwingTheme.MUTED : SwingTheme.BLUE);
        card.add(time, BorderLayout.WEST);

        final JPanel centre = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        centre.setOpaque(false);

        final JLabel title = new JLabel(travel
                ? "\u21b3 " + row.getTitle() : row.getTitle());
        title.setFont(travel ? SwingTheme.SMALL : SwingTheme.BODY.deriveFont(Font.BOLD));
        title.setForeground(travel ? SwingTheme.MUTED : SwingTheme.NAVY);
        centre.add(title);

        if (row.isLocked()) {
            final JLabel badge = SwingTheme.badge("Locked", SwingTheme.BLUE, SwingTheme.BLUE_SOFT);
            badge.setIcon(new LockIcon(true, 11));
            badge.setIconTextGap(4);
            centre.add(badge);
        } else if (row.isMoved()) {
            centre.add(SwingTheme.badge("Moved", SwingTheme.MUTED, SwingTheme.BACKGROUND));
        }
        card.add(centre, BorderLayout.CENTER);

        if (!travel && !row.getReason().isEmpty()) {
            final JLabel reason = new JLabel(row.getReason());
            reason.setFont(SwingTheme.SMALL);
            reason.setForeground(SwingTheme.MUTED);
            card.add(reason, BorderLayout.SOUTH);
        }
        card.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                card.getPreferredSize().height));
        return card;
    }

    private JPanel eventCard(ScheduledEvent event, DayPlanState state,
                             List<WeatherWarning> hourlyWeather,
                             ScheduledEvent arrivedBy) {
        final boolean locked = state.getLockedEventIds().contains(event.getId());
        final JPanel card = new JPanel(new BorderLayout(12, 5));
        SwingTheme.styleCard(card);
        // A journey is a connector between two activities, not an activity, and the twelve
        // pixels of padding an activity card carries are more than a ten-minute gap has to
        // give. Trimming them lets a short hop label itself inside its own slot instead of
        // reaching down over the place it leads to.
        if (event.getEventType() == EventType.TRAVEL) {
            // The preview renderer already gives journeys this quiet contrast. The normal
            // Day Plan used the default white card instead, so after Apply a 24px connector
            // visually vanished into the white timeline even though the row still existed.
            card.setBackground(SwingTheme.TRAVEL_SURFACE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SwingTheme.LINE, 1, true),
                    BorderFactory.createEmptyBorder(2, 14, 2, 14)));
        }
        if (event.getActivity() != null) {
            card.setBackground(SwingTheme.categorySurface(
                    event.getActivity().getCategory()));
        }
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        makeSelectable(card, event);
        // The timeline may reserve a few pixels above an activity for a two-minute travel
        // connector. Retain the real visual border so every layout pass can add that space
        // from scratch instead of nesting another empty border after each resize.
        card.putClientProperty("trippy.baseBorder", card.getBorder());
        card.putClientProperty("trippy.arrivalMinutes", arrivedBy == null ? 0
                : minutesBetween(arrivedBy.getStartTime(), arrivedBy.getEndTime()));

        final String name = event.getActivity() == null
                ? (event.getNotes().isEmpty() ? event.getEventType().toString() : event.getNotes())
                : event.getActivity().getName();
        String displayedName = name;
        if (event.getActivity() != null) {
            displayedName = ActivityCategoryPresentation.decorate(
                    event.getActivity().getCategory(), name);
        }
        final String visibleName = displayedName;

        if (event.getEventType() == EventType.TRAVEL) {
            final JLabel route = new JLabel("\u21b3 " + visibleName);
            route.setFont(SwingTheme.SMALL);
            route.setForeground(SwingTheme.MUTED);
            route.setToolTipText(name);
            route.getAccessibleContext().setAccessibleName(name);
            card.add(route, BorderLayout.CENTER);

            final JLabel time = new JLabel(TimeDisplay.range(
                    event.getStartTime(), event.getEndTime()));
            time.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
            time.setForeground(SwingTheme.MUTED);
            card.add(time, BorderLayout.EAST);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    card.getPreferredSize().height));
            card.putClientProperty("trippy.basePreferredHeight",
                    card.getPreferredSize().height);
            return card;
        }

        final JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);

        // The venue and clock used to compete in one non-wrapping label. A long venue could
        // therefore erase the end time and still provide no way to recover the hidden name.
        // Separate lines make the clock non-negotiable; JLabel's deliberate ellipsis plus the
        // tooltip keeps a long venue accessible without forcing the three actions off-card.
        final JLabel title = new JLabel(visibleName);
        title.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, title.getPreferredSize().height));
        title.setForeground(SwingTheme.NAVY);
        title.setToolTipText(name);
        title.getAccessibleContext().setAccessibleName(name);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(title);

        final JLabel time = new JLabel(TimeDisplay.range(event.getStartTime(), event.getEndTime()));
        time.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
        time.setForeground(SwingTheme.BLUE);
        time.setMaximumSize(new Dimension(Integer.MAX_VALUE, time.getPreferredSize().height));
        time.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(time);
        card.putClientProperty("trippy.timeLabel", time);

        if (!event.getNotes().isEmpty() && !name.equals(event.getNotes())) {
            final JLabel notes = new JLabel(event.getNotes());
            notes.setFont(SwingTheme.SMALL);
            notes.setForeground(SwingTheme.MUTED);
            notes.setAlignmentX(Component.LEFT_ALIGNMENT);
            details.add(notes);
        }

        // Shiyuan's per-hour forecast. Each hour is one line, shortened to a width that
        // cannot force the panel to scroll sideways, with the whole reading in a tooltip.
        if (event.getEventType() == EventType.ACTIVITY && !hourlyWeather.isEmpty()) {
            for (WeatherWarning warning : hourlyWeather) {
                final String full = TimeDisplay.format(warning.getTime()) + " \u00b7 "
                        + warning.getWeatherCondition() + " \u00b7 " + warning.getMessage();
                final JLabel line = new JLabel(truncate(full, 64));
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
            final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            actions.setOpaque(false);
            actions.add(lockToggle(event, name, locked));

            // Alex's manual controls, disabled rather than hidden when no controller is
            // wired, so the layout does not shift between the two cases.
            final JButton edit = SwingTheme.secondaryButton("Edit");
            final JButton remove = SwingTheme.secondaryButton("Remove");
            // During a Preview, Remove edits the proposal itself and saves nothing. Edit still
            // has no draft-only form, so it stays disabled there rather than writing through to
            // the itinerary the traveller has not agreed to yet.
            final boolean previewing = state.getStatus() == AutoScheduleStatus.PREVIEW;
            edit.setEnabled(manualPlanController != null && canEditRowsNow(state));
            remove.setEnabled(previewing
                    ? canEditItinerary()
                    : manualPlanController != null && canEditRowsNow(state));
            if (previewing) {
                edit.setToolTipText("Editing times is only available on your saved Day Plan. "
                        + "Apply or Cancel this proposal first.");
                remove.setToolTipText("Take this out of the proposal. Nothing is saved until "
                        + "you choose Apply.");
            }
            edit.getAccessibleContext().setAccessibleName("Edit " + name);
            remove.getAccessibleContext().setAccessibleName("Remove " + name);
            edit.addActionListener(action -> {
                if (canEditRowsNow(viewModel.getState())) {
                    editEvent(event);
                }
            });
            remove.addActionListener(action -> {
                final DayPlanState now = viewModel.getState();
                // A Preview removal never reaches the saved plan; it edits the draft on screen.
                if (now.getStatus() == AutoScheduleStatus.PREVIEW) {
                    if (canEditItinerary()) {
                        autoScheduleController.removeFromProposal(event.getId());
                    }
                    return;
                }
                // Re-checked at click time as well as at render time: a Preview can open
                // between the two, and a stale enabled button must not be a way in.
                if (canEditRowsNow(now) && RemovalDialogs.confirm(
                        this,
                        "Remove from Day Plan",
                        "Remove \"" + name + "\" from your Day Plan?")) {
                    manualPlanController.remove(event.getId());
                }
            });
            actions.add(edit);
            actions.add(remove);

            card.add(actions, BorderLayout.EAST);
            card.putClientProperty("trippy.actions", actions);
            card.putClientProperty("trippy.actionsSouth", Boolean.FALSE);
        }
        card.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                card.getPreferredSize().height));
        card.putClientProperty("trippy.basePreferredHeight", card.getPreferredSize().height);
        return card;
    }

    /** Google Calendar-style day grid whose event blocks are positioned by start time. */
    private final class ScheduleTimeline extends JPanel implements Scrollable {
        static final int HOUR_HEIGHT = 72;
        private static final int TIME_GUTTER = 68;
        private static final int EVENT_GAP = 8;
        /** The cards in schedule order; z-order no longer matches, so index lookups cannot. */
        private final List<JPanel> cards = new java.util.ArrayList<>();
        private DayPlanState state;
        /** The stretch of day actually drawn. Narrower than the trip while previewing. */
        private LocalTime viewStart = LocalTime.of(9, 0);
        private LocalTime viewEnd = LocalTime.of(21, 0);
        /** Current proportional scale; grows only when an activity would clip. */
        private int hourHeight = HOUR_HEIGHT;

        private ScheduleTimeline() {
            setLayout(null);
            setName("Day schedule timeline");
            getAccessibleContext().setAccessibleName("Day schedule timeline");
            setBackground(SwingTheme.PANEL);
            setBorder(BorderFactory.createLineBorder(SwingTheme.LINE, 1, true));
        }

        private void setSchedule(DayPlanState updatedState) {
            state = updatedState;
            hourHeight = HOUR_HEIGHT;
            removeAll();
            cards.clear();
            final List<JPanel> travelCards = new java.util.ArrayList<>();
            final List<ScheduledEvent> shown = displayed(updatedState);
            for (int i = 0; i < shown.size(); i++) {
                final ScheduledEvent event = shown.get(i);
                final ScheduledEvent arrivedBy = i > 0
                        && shown.get(i - 1).getEventType() == EventType.TRAVEL
                        ? shown.get(i - 1) : null;
                final JPanel card = eventCard(event, updatedState,
                        updatedState.getHourlyWeatherFor(event), arrivedBy);
                add(card);
                cards.add(card);
                if (event.getEventType() == EventType.TRAVEL) {
                    travelCards.add(card);
                }
                // Not while previewing. These cards are a proposal, and dragging one would
                // write a proposed time onto the saved day behind the traveller's back --
                // exactly what Apply and Cancel exist to prevent.
                if (event.getEventType() == EventType.ACTIVITY
                        && manualPlanController != null
                        && updatedState.getStatus() != AutoScheduleStatus.PREVIEW) {
                    installDragHandling(card, event);
                }
            }
            // A short journey needs more height than its slot to stay legible, so its
            // connector necessarily runs past its own interval and into the next activity's.
            // Both stay readable because the activity below reserves that overlap as blank
            // padding (see eventCard), and the connector is painted in front of it. Sending
            // travel to the back instead only chose which of the two to lose.
            for (JPanel travel : travelCards) {
                setComponentZOrder(travel, 0);
            }
            fitWindowTo(displayed(updatedState), updatedState);
            // Worked out here rather than in doLayout. It depends only on the events, and
            // changing it mid-layout meant the scroll pane had already sized its view: the
            // timeline grew, the viewport did not hear about it, and the extra hours sat
            // below the scrollable extent where nothing could reach them.
            hourHeight = requiredHourHeight(displayed(updatedState));
            updatePreferredSize();
            revalidate();
            repaint();
        }

        /** The proposal while previewing, the saved day otherwise. */
        private List<ScheduledEvent> displayed(DayPlanState state) {
            return state.getStatus() == AutoScheduleStatus.PREVIEW
                    ? proposalAsEvents(state) : state.getEvents();
        }

        /**
         * The window to draw: the whole trip normally, and just the proposal while previewing.
         *
         * <p>A proposal that runs 11:30 to 2:30 drawn against a 9-to-9 day put five hours of
         * empty timeline between the schedule and everything explaining it, which is what made
         * the explanation unreachable without scrolling. Half an hour of padding keeps the
         * proposal from sitting flush against the edges without reintroducing the empty day.</p>
         */
        private void fitWindowTo(List<ScheduledEvent> events, DayPlanState updatedState) {
            viewStart = tripStart;
            viewEnd = tripEnd;
            if (updatedState.getStatus() != AutoScheduleStatus.PREVIEW || events.isEmpty()) {
                return;
            }
            LocalTime first = events.get(0).getStartTime();
            LocalTime last = events.get(0).getEndTime();
            for (ScheduledEvent event : events) {
                if (event.getStartTime().isBefore(first)) {
                    first = event.getStartTime();
                }
                if (event.getEndTime().isAfter(last)) {
                    last = event.getEndTime();
                }
            }
            // The window must contain the schedule, not the other way round. Clamping it to the
            // trip's own hours meant a proposal running past them was drawn at a y below the
            // component's own bottom edge -- present, positioned correctly, and unreachable by
            // any amount of scrolling. Whatever is drawn has to be inside what can be scrolled.
            final LocalTime earliest = first.isBefore(tripStart) ? first : tripStart;
            final LocalTime latest = last.isAfter(tripEnd) ? last : tripEnd;

            final LocalTime padded = first.minusMinutes(30);
            viewStart = padded.isBefore(earliest) || padded.isAfter(first) ? earliest : padded;
            final LocalTime paddedEnd = last.plusMinutes(30);
            viewEnd = paddedEnd.isAfter(latest) || paddedEnd.isBefore(last) ? latest : paddedEnd;
            if (!viewEnd.isAfter(viewStart)) {
                viewStart = earliest;
                viewEnd = latest;
            }
        }

        private void updatePreferredSize() {
            final int minutes = Math.max(60, minutesBetween(viewStart, viewEnd));
            final int height = Math.max(360, minutes * hourHeight / 60 + 1);
            // A second guard on the same promise: card heights have their own floors, so a
            // short activity late in the day finishes lower than its end time alone implies.
            // Worked out from the same arithmetic doLayout uses rather than from laid-out
            // bounds, because this runs before any card has been given one.
            final int tallest = Math.max(height, lowestDrawnBottom() + 4);
            setPreferredSize(new Dimension(520, tallest));
            setMinimumSize(new Dimension(0, tallest));
        }

        /** Where the lowest card will be drawn, by the same rules {@code doLayout} applies. */
        private int lowestDrawnBottom() {
            if (state == null) {
                return 0;
            }
            int lowest = 0;
            for (ScheduledEvent event : displayed(state)) {
                final int start = signedMinutesBetween(viewStart, event.getStartTime());
                final int duration = Math.max(1, minutesBetween(
                        event.getStartTime(), event.getEndTime()));
                final int y = Math.max(0, start * hourHeight / 60 + 2);
                final int minimumHeight = event.getEventType() == EventType.TRAVEL
                        ? MINIMUM_CONNECTOR_HEIGHT : 64;
                lowest = Math.max(lowest,
                        y + Math.max(minimumHeight, duration * hourHeight / 60 - 4));
            }
            return lowest;
        }

        /**
         * Keeps the time scale proportional while making it tall enough for real card content.
         *
         * <p>This is deliberately based only on activity cards. Travel remains a compact
         * connector and may borrow blank padding at the top of its destination; allowing a
         * two-minute leg to set the global scale would turn one hour into twelve screens.</p>
         */
        private int requiredHourHeight(List<ScheduledEvent> events) {
            int required = HOUR_HEIGHT;
            // The connector overrun depends on the scale itself. A few fixed-point passes
            // converge because increasing the scale only makes the overrun smaller.
            for (int pass = 0; pass < 4; pass++) {
                int next = required;
                for (int i = 0; i < events.size() && i < cards.size(); i++) {
                    final ScheduledEvent event = events.get(i);
                    if (event.getEventType() != EventType.ACTIVITY) {
                        continue;
                    }
                    final JPanel card = cards.get(i);
                    final int content = (Integer) card.getClientProperty(
                            "trippy.basePreferredHeight");
                    final int arrivalMinutes = (Integer) card.getClientProperty(
                            "trippy.arrivalMinutes");
                    final int arrivalSlot = arrivalMinutes * required / 60;
                    final int connectorOverrun = Math.max(
                            0, MINIMUM_CONNECTOR_HEIGHT - arrivalSlot);
                    final int duration = Math.max(1, minutesBetween(
                            event.getStartTime(), event.getEndTime()));
                    final int pixelsNeeded = content + connectorOverrun + 4;
                    next = Math.max(next,
                            (pixelsNeeded * 60 + duration - 1) / duration);
                }
                if (next == required) {
                    break;
                }
                required = next;
            }
            return required;
        }

        private void reserveConnectorSpace(JPanel card) {
            final Border base = (Border) card.getClientProperty("trippy.baseBorder");
            final int arrivalMinutes = (Integer) card.getClientProperty("trippy.arrivalMinutes");
            final int arrivalSlot = arrivalMinutes * hourHeight / 60;
            final int overrun = Math.max(0, MINIMUM_CONNECTOR_HEIGHT - arrivalSlot);
            card.setBorder(overrun == 0 ? base : BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(overrun, 0, 0, 0), base));
        }

        /** Gives the clock a full line at narrow widths without hiding any action. */
        private void arrangeActions(JPanel card, int cardWidth) {
            final Border base = (Border) card.getClientProperty("trippy.baseBorder");
            card.setBorder(base);
            final JPanel actions = (JPanel) card.getClientProperty("trippy.actions");
            final JLabel time = (JLabel) card.getClientProperty("trippy.timeLabel");
            if (actions == null || time == null) {
                card.putClientProperty("trippy.basePreferredHeight",
                        card.getPreferredSize().height);
                return;
            }
            final int horizontalInsets = card.getInsets().left + card.getInsets().right;
            final int besideActions = cardWidth - horizontalInsets
                    - actions.getPreferredSize().width - 12;
            final boolean stack = besideActions < time.getPreferredSize().width + 36;
            final boolean alreadyStacked = Boolean.TRUE.equals(
                    card.getClientProperty("trippy.actionsSouth"));
            if (stack != alreadyStacked) {
                card.remove(actions);
                card.add(actions, stack ? BorderLayout.SOUTH : BorderLayout.EAST);
                card.putClientProperty("trippy.actionsSouth", stack);
            }
            card.putClientProperty("trippy.basePreferredHeight",
                    card.getPreferredSize().height);
        }

        @Override
        public void doLayout() {
            if (state == null) return;
            final int cardWidth = Math.max(160, getWidth() - TIME_GUTTER - EVENT_GAP * 2);
            final List<ScheduledEvent> events = displayed(state);
            for (JPanel card : cards) {
                arrangeActions(card, cardWidth);
            }
            // Normally already settled by setSchedule; this is the safety net for a layout
            // that arrives before one, and it asks for another pass rather than resizing
            // silently underneath the viewport.
            final int neededHourHeight = requiredHourHeight(events);
            if (hourHeight != neededHourHeight) {
                hourHeight = neededHourHeight;
                updatePreferredSize();
                SwingUtilities.invokeLater(this::revalidate);
            }
            for (int i = 0; i < events.size() && i < cards.size(); i++) {
                final ScheduledEvent event = events.get(i);
                final JPanel card = cards.get(i);
                // Only the destination activity borrows blank space for a short connector.
                // Applying that padding to the connector itself gives a 24px card 27px of
                // top inset, leaving its labels a negative height and therefore invisible.
                if (event.getEventType() == EventType.ACTIVITY) {
                    reserveConnectorSpace(card);
                } else {
                    card.setBorder((Border) card.getClientProperty("trippy.baseBorder"));
                }
                final int start = signedMinutesBetween(viewStart, event.getStartTime());
                final int duration = Math.max(1, minutesBetween(
                        event.getStartTime(), event.getEndTime()));
                final int y = Math.max(0, start * hourHeight / 60 + 2);
                // Travel is a thin connector, so it keeps a much smaller floor than an
                // activity card. Forcing every row to 64px made a half-hour journey taller
                // than its own slot and draw straight over the activity it leads to; a fixed
                // small floor then cut the label in half, because the floor has to cover the
                // card's own padding as well as the line of text. Asking the card what it
                // needs keeps both ends honest.
                final int minimumHeight = event.getEventType() == EventType.TRAVEL
                        ? MINIMUM_CONNECTOR_HEIGHT : 64;
                final int height = Math.max(minimumHeight, duration * hourHeight / 60 - 4);
                card.setBounds(TIME_GUTTER + EVENT_GAP, y, cardWidth, height);
                // This parent uses absolute positioning. A newly rebuilt 24px travel card
                // can otherwise be painted before Swing schedules a second validation pass,
                // leaving its BorderLayout children at 0x0: the connector background is
                // present, but its route and time are invisible. Lay out each card after its
                // final bounds are known so Apply's immediate repaint is complete.
                card.doLayout();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            final Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setFont(SwingTheme.SMALL);
            final int totalMinutes = Math.max(60, minutesBetween(viewStart, viewEnd));
            for (int minute = 0; minute <= totalMinutes; minute += 60) {
                final int y = minute * hourHeight / 60;
                final LocalTime time = viewStart.plusMinutes(minute);
                final String label = TimeDisplay.format(time);
                g2.setColor(SwingTheme.MUTED);
                // During a resize Swing may paint this component at its old height before
                // the scroll pane accepts the new preferred height. Off-screen ticks must
                // remain off screen: clamping every baseline to the bottom edge stacks all
                // remaining clock labels into one unreadable string of digits.
                if (y + 14 <= getHeight() - 4) {
                    g2.drawString(label, 8, y + 14);
                }
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
            return orientation == SwingConstants.VERTICAL ? hourHeight / 2 : 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect,
                                               int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL
                    ? Math.max(hourHeight, visibleRect.height - hourHeight) : 64;
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
            final MouseAdapter drag = new MouseAdapter() {
                private int pointerOffset;
                private boolean moved;

                @Override
                public void mousePressed(MouseEvent mouseEvent) {
                    final java.awt.Point point = SwingUtilities.convertPoint(
                            mouseEvent.getComponent(), mouseEvent.getPoint(), ScheduleTimeline.this);
                    pointerOffset = point.y - card.getY();
                    moved = false;
                }

                @Override
                public void mouseDragged(MouseEvent mouseEvent) {
                    final java.awt.Point point = SwingUtilities.convertPoint(
                            mouseEvent.getComponent(), mouseEvent.getPoint(), ScheduleTimeline.this);
                    final int duration = Math.max(1, minutesBetween(
                            event.getStartTime(), event.getEndTime()));
                    final int total = Math.max(1, minutesBetween(viewStart, viewEnd));
                    final int latestStart = Math.max(0, total - duration);
                    final int maximumY = latestStart * hourHeight / 60;
                    final int y = Math.max(0, Math.min(maximumY, point.y - pointerOffset));
                    card.setLocation(card.getX(), y);
                    moved = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent mouseEvent) {
                    if (!moved) return;
                    final int duration = Math.max(1, minutesBetween(
                            event.getStartTime(), event.getEndTime()));
                    final LocalTime start = draggedStartFor(
                            viewStart, viewEnd, card.getY(), hourHeight, duration);
                    final LocalTime end = start.plusMinutes(duration);
                    manualPlanController.edit(
                            event.getId(), start.toString(), end.toString(), event.getNotes());
                }
            };
            addDragListener(card, drag);
            card.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            final String tooltip = card.getToolTipText();
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
        final int total = Math.max(1, minutesBetween(dayStart, dayEnd));
        final int latestStart = Math.max(0, total - Math.max(1, durationMinutes));
        final double rawMinutes = Math.max(0, y) * 60.0 / Math.max(1, hourHeight);
        final int snapped = (int) Math.round(rawMinutes / 15.0) * 15;
        return dayStart.plusMinutes(Math.max(0, Math.min(latestStart, snapped)));
    }

    private static int minutesBetween(LocalTime start, LocalTime end) {
        final int minutes = (int) java.time.Duration.between(start, end).toMinutes();
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
        final JToggleButton toggle = new JToggleButton(new LockIcon(locked, 14));
        toggle.setSelected(locked);
        toggle.setFocusPainted(true);
        toggle.setOpaque(false);
        toggle.setContentAreaFilled(false);
        toggle.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(locked ? SwingTheme.BLUE : SwingTheme.LINE),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        final String at = TimeDisplay.format(event.getStartTime());
        final String action = locked ? "Unlock " + name : "Lock " + name + " at " + at;
        toggle.setToolTipText(locked
                ? action + " so Autoschedule may move it"
                : action + " so Autoschedule keeps it there");
        toggle.getAccessibleContext().setAccessibleName(action);
        toggle.getAccessibleContext().setAccessibleDescription(locked
                ? name + " is pinned to " + at
                : name + " is not pinned and may be moved");
        toggle.setEnabled(canEditItinerary());
        toggle.addActionListener(action2 -> {
            if (canEditItinerary()) {
                autoScheduleController.toggleLock(event.getId());
            }
        });
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
        final TimeSelectorPanel start = new TimeSelectorPanel(event.getStartTime());
        final TimeSelectorPanel end = new TimeSelectorPanel(event.getEndTime());
        final JTextField notes = new JTextField(event.getNotes());
        final JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(new JLabel("Start time"));
        form.add(start);
        form.add(new JLabel("End time"));
        form.add(end);
        form.add(new JLabel("Notes"));
        form.add(notes);
        final int choice = JOptionPane.showConfirmDialog(
                this, form, "Edit scheduled event",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            manualPlanController.edit(
                    event.getId(), TimeDisplay.format(start.getTime()),
                    TimeDisplay.format(end.getTime()), notes.getText());
        }
    }
}
