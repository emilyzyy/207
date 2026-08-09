package views;

import interface_adapter.controllers.ManualPlanController;
import interface_adapter.viewmodels.TimeDisplay;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.TripOptionsViewModel;
import use_case.usecases.AvailableTimeSlotFinder;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Confirms a manually selected time while previewing the activity in the full day. */
public final class AddToPlanDialog extends JDialog {
    private final Activity activity;
    private final List<ScheduledEvent> events;
    private final LocalTime dayStart;
    private final LocalTime dayEnd;
    private final ManualPlanController controller;
    private final TimeSelectorPanel start;
    private final TimeSelectorPanel end;
    private final JLabel error = new JLabel(" ");
    private final PreviewTimeline timeline;
    private boolean updatingTimes;

    public static void open(Component parent, Activity activity,
                            DayPlanViewModel dayPlan,
                            TripOptionsViewModel tripOptions,
                            ManualPlanController controller) {
        LocalTime dayStart = tripOptions.getState().getStartTime();
        LocalTime dayEnd = tripOptions.getState().getEndTime();
        AvailableTimeSlotFinder.Slot slot = new AvailableTimeSlotFinder().find(
                dayStart, dayEnd, dayPlan.getState().getEvents());
        if (slot == null) {
            JOptionPane.showMessageDialog(parent,
                    "Your Day Plan is full. Remove or shorten an activity before adding another.",
                    "Day Plan is full", JOptionPane.ERROR_MESSAGE);
            return;
        }
        new AddToPlanDialog(parent, activity, dayPlan.getState().getEvents(),
                dayStart, dayEnd, controller, slot).setVisible(true);
    }

    public AddToPlanDialog(Component parent, Activity activity,
                           List<ScheduledEvent> events, LocalTime dayStart,
                           LocalTime dayEnd, ManualPlanController controller,
                           AvailableTimeSlotFinder.Slot initial) {
        super(SwingUtilities.getWindowAncestor(parent), "Add to Day Plan",
                ModalityType.APPLICATION_MODAL);
        this.activity = activity;
        this.events = new ArrayList<>(events);
        this.events.sort(Comparator.comparing(ScheduledEvent::getStartTime));
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.controller = controller;
        timeline = new PreviewTimeline();
        start = new TimeSelectorPanel(initial.getStart());
        end = new TimeSelectorPanel(initial.getEnd());

        JPanel left = new JPanel(new GridBagLayout());
        left.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 18));
        left.setBackground(SwingTheme.PANEL);
        addField(left, 0, "Start", start);
        addField(left, 1, "End", end);
        error.setForeground(SwingTheme.ERROR);
        error.setFont(SwingTheme.SMALL);
        GridBagConstraints errorAt = new GridBagConstraints();
        errorAt.gridx = 0; errorAt.gridy = 2; errorAt.gridwidth = 2;
        errorAt.anchor = GridBagConstraints.WEST;
        errorAt.insets = new Insets(10, 0, 0, 0);
        left.add(error, errorAt);

        JLabel instruction = new JLabel("or drag and drop!", JLabel.CENTER);
        instruction.setFont(SwingTheme.HEADING);
        instruction.setForeground(SwingTheme.NAVY);
        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 12));
        right.setBackground(SwingTheme.PANEL);
        right.add(instruction, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(timeline);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        right.add(scroll, BorderLayout.CENTER);

        JPanel body = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        body.setBackground(SwingTheme.PANEL);
        body.add(left);
        body.add(right);

        JButton cancel = SwingTheme.secondaryButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton ok = SwingTheme.primaryButton("OK");
        ok.addActionListener(event -> confirm());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        actions.setBackground(SwingTheme.PANEL);
        actions.add(cancel);
        actions.add(ok);

        setLayout(new BorderLayout());
        add(body, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        start.addChangeListener(this::timesChanged);
        end.addChangeListener(this::timesChanged);
        timeline.rebuild();
        setPreferredSize(new Dimension(840, 620));
        pack();
        setLocationRelativeTo(parent);
    }

    private void addField(JPanel panel, int row, String label, Component field) {
        GridBagConstraints at = new GridBagConstraints();
        at.gridy = row; at.gridx = 0; at.anchor = GridBagConstraints.EAST;
        at.insets = new Insets(6, 0, 6, 8);
        panel.add(new JLabel(label), at);
        at.gridx = 1; at.anchor = GridBagConstraints.WEST;
        panel.add(field, at);
    }

    int displayedPlannedActivityCount() {
        return Math.max(0, timeline.getComponentCount() - 1);
    }

    private void timesChanged() {
        if (updatingTimes) return;
        String problem = validationProblem(start.getTime(), end.getTime());
        error.setText(problem == null ? " " : problem);
        timeline.rebuild();
    }

    private void setSelectedTimes(LocalTime selectedStart, LocalTime selectedEnd) {
        updatingTimes = true;
        start.setTime(selectedStart);
        end.setTime(selectedEnd);
        updatingTimes = false;
        timeline.rebuild();
    }

    private void confirm() {
        String problem = validationProblem(start.getTime(), end.getTime());
        if (problem != null) {
            error.setText(problem);
            return;
        }
        controller.add(activity.getId(), start.getTime(), end.getTime());
        dispose();
    }

    private String validationProblem(LocalTime proposedStart, LocalTime proposedEnd) {
        if (!proposedEnd.isAfter(proposedStart)) return "End time must follow start time.";
        if (proposedStart.isBefore(dayStart) || proposedEnd.isAfter(dayEnd)) {
            return "Keep the activity inside the day plan.";
        }
        for (ScheduledEvent event : events) {
            if (proposedStart.isBefore(event.getEndTime())
                    && event.getStartTime().isBefore(proposedEnd)) {
                return "That time overlaps another item in your Day Plan.";
            }
        }
        return null;
    }

    private final class PreviewTimeline extends JPanel implements Scrollable {
        private static final int HOUR_HEIGHT = 72;
        private static final int GUTTER = 68;
        private JPanel proposedCard;

        private PreviewTimeline() {
            setLayout(null);
            setBackground(SwingTheme.PANEL);
            setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
            int minutes = (int) Duration.between(dayStart, dayEnd).toMinutes();
            setPreferredSize(new Dimension(450, Math.max(360, minutes * HOUR_HEIGHT / 60)));
        }

        private void rebuild() {
            removeAll();
            List<ScheduledEvent> ordered = new ArrayList<>(events);
            ordered.sort(Comparator.comparing(ScheduledEvent::getStartTime));
            for (ScheduledEvent event : ordered) {
                String name = event.getActivity() == null ? event.getNotes()
                        : event.getActivity().getName();
                JPanel existingCard = card(name + "  " + TimeDisplay.range(
                        event.getStartTime(), event.getEndTime()), false);
                if (event.getActivity() != null) {
                    existingCard.setBackground(SwingTheme.categorySurface(
                            event.getActivity().getCategory()));
                }
                add(existingCard);
            }
            proposedCard = card(activity.getName() + "  "
                    + TimeDisplay.range(start.getTime(), end.getTime()), true);
            proposedCard.setBackground(SwingTheme.categorySurface(activity.getCategory()));
            installDrag(proposedCard);
            add(proposedCard);
            revalidate();
            repaint();
        }

        private JPanel card(String name, boolean proposed) {
            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(proposed ? SwingTheme.BLUE_SOFT : SwingTheme.PANEL);
            card.setBorder(BorderFactory.createLineBorder(
                    proposed ? SwingTheme.BLUE : SwingTheme.LINE, proposed ? 2 : 1));
            JLabel text = new JLabel(name);
            text.setFont(SwingTheme.BODY.deriveFont(proposed ? Font.BOLD : Font.PLAIN));
            text.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            card.add(text);
            return card;
        }

        @Override public void doLayout() {
            int width = Math.max(150, getWidth() - GUTTER - 14);
            for (int index = 0; index < events.size() && index < getComponentCount() - 1; index++) {
                ScheduledEvent event = events.get(index);
                place(getComponent(index), event.getStartTime(), event.getEndTime(), width);
            }
            if (proposedCard != null) place(proposedCard, start.getTime(), end.getTime(), width);
        }

        private void place(Component component, LocalTime from, LocalTime until, int width) {
            int offset = (int) Duration.between(dayStart, from).toMinutes();
            int duration = Math.max(15, (int) Duration.between(from, until).toMinutes());
            component.setBounds(GUTTER + 6, offset * HOUR_HEIGHT / 60,
                    width, Math.max(28, duration * HOUR_HEIGHT / 60 - 3));
        }

        private void installDrag(JPanel card) {
            MouseAdapter drag = new MouseAdapter() {
                private int offset;
                private boolean moved;
                private LocalTime originalStart;
                private LocalTime originalEnd;
                @Override public void mousePressed(MouseEvent event) {
                    Point point = SwingUtilities.convertPoint(
                            event.getComponent(), event.getPoint(), PreviewTimeline.this);
                    offset = point.y - card.getY(); moved = false;
                    originalStart = start.getTime();
                    originalEnd = end.getTime();
                }
                @Override public void mouseDragged(MouseEvent event) {
                    Point point = SwingUtilities.convertPoint(
                            event.getComponent(), event.getPoint(), PreviewTimeline.this);
                    int duration = Math.max(15,
                            (int) Duration.between(start.getTime(), end.getTime()).toMinutes());
                    int total = (int) Duration.between(dayStart, dayEnd).toMinutes();
                    int maxY = Math.max(0, (total - duration) * HOUR_HEIGHT / 60);
                    card.setLocation(card.getX(), Math.max(0, Math.min(maxY, point.y - offset)));
                    moved = true; repaint();
                }
                @Override public void mouseReleased(MouseEvent event) {
                    if (!moved) return;
                    int duration = Math.max(15,
                            (int) Duration.between(start.getTime(), end.getTime()).toMinutes());
                    LocalTime movedStart = DayPlanPanel.draggedStartFor(
                            dayStart, dayEnd, card.getY(), HOUR_HEIGHT, duration);
                    LocalTime movedEnd = movedStart.plusMinutes(duration);
                    String problem = validationProblem(movedStart, movedEnd);
                    if (problem == null) {
                        error.setText(" ");
                        setSelectedTimes(movedStart, movedEnd);
                    } else {
                        error.setText(problem);
                        setSelectedTimes(originalStart, originalEnd);
                    }
                }
            };
            card.addMouseListener(drag);
            card.addMouseMotionListener(drag);
            for (Component child : card.getComponents()) {
                child.addMouseListener(drag);
                child.addMouseMotionListener(drag);
            }
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            int total = (int) Duration.between(dayStart, dayEnd).toMinutes();
            for (int minute = 0; minute <= total; minute += 60) {
                int y = minute * HOUR_HEIGHT / 60;
                g.setColor(SwingTheme.MUTED);
                g.drawString(TimeDisplay.format(dayStart.plusMinutes(minute)), 6, y + 12);
                g.setColor(SwingTheme.LINE);
                g.drawLine(GUTTER, y, getWidth(), y);
            }
            g.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 72; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
