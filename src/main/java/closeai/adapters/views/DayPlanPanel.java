package closeai.adapters.views;

import closeai.adapters.controllers.OptimizeItineraryController;
import closeai.adapters.controllers.ManualPlanController;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.domain.entities.ScheduledEvent;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/** Day-plan view with the milestone's single active workflow. */
public final class DayPlanPanel extends JPanel {
    private final DayPlanViewModel viewModel;
    private final OptimizeItineraryController optimizeController;
    private final ManualPlanController manualPlanController;
    private final JPanel eventList = new JPanel();
    private final JLabel status = new JLabel();
    private JButton optimizeButton;
    private Runnable openCalendarAction = () -> { };

    public DayPlanPanel(
            DayPlanViewModel viewModel,
            OptimizeItineraryController optimizeController) {
        this(viewModel, optimizeController, null);
    }

    public DayPlanPanel(
            DayPlanViewModel viewModel,
            OptimizeItineraryController optimizeController,
            ManualPlanController manualPlanController) {
        this.viewModel = viewModel;
        this.optimizeController = optimizeController;
        this.manualPlanController = manualPlanController;

        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(header(), BorderLayout.NORTH);

        eventList.setLayout(new BoxLayout(eventList, BoxLayout.Y_AXIS));
        eventList.setBackground(SwingTheme.PANEL);
        JScrollPane scroll = new JScrollPane(eventList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(scroll, BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
    }

    public void setOpenCalendarAction(Runnable action) {
        openCalendarAction = action == null ? () -> { } : action;
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
        JLabel contract = new JLabel(
                "First-pass compaction · current itinerary activities only");
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
        wrapper.add(status);
        wrapper.add(Box.createVerticalStrut(8));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        buttons.setOpaque(false);
        optimizeButton = SwingTheme.primaryButton("Optimize Itinerary");
        optimizeButton.addActionListener(event -> optimizeController.execute());
        buttons.add(optimizeButton);

        JButton calendar = new JButton("Calendar View");
        calendar.setFont(SwingTheme.BODY);
        calendar.addActionListener(event -> openCalendarAction.run());
        buttons.add(calendar);
        wrapper.add(buttons);
        return wrapper;
    }

    private void render(DayPlanState state) {
        eventList.removeAll();
        if (state.getEvents().isEmpty()) {
            JLabel empty = new JLabel("No activities are currently scheduled.");
            empty.setFont(SwingTheme.BODY);
            empty.setForeground(SwingTheme.MUTED);
            eventList.add(empty);
        } else {
            for (ScheduledEvent event : state.getEvents()) {
                eventList.add(eventCard(event));
                eventList.add(Box.createVerticalStrut(8));
            }
        }
        String message = state.getMessage().isEmpty()
                ? "Ready to compact the current itinerary."
                : state.getMessage();
        status.setText(message);
        status.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.SUCCESS);
        optimizeButton.setEnabled(!state.getTripId().isEmpty());
        eventList.revalidate();
        eventList.repaint();
    }

    private JPanel eventCard(ScheduledEvent event) {
        JPanel card = new JPanel(new BorderLayout(12, 5));
        SwingTheme.styleCard(card);
        JLabel time = new JLabel(event.getStartTime() + " – " + event.getEndTime());
        time.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        time.setForeground(SwingTheme.BLUE);
        card.add(time, BorderLayout.WEST);

        String name = event.getActivity() == null
                ? event.getEventType().toString()
                : event.getActivity().getName();
        JLabel details = new JLabel("<html><b>" + name + "</b><br>"
                + event.getNotes() + "</html>");
        details.setFont(SwingTheme.BODY);
        details.setForeground(SwingTheme.NAVY);
        card.add(details, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        JButton edit = SwingTheme.secondaryButton("Edit");
        JButton remove = SwingTheme.secondaryButton("Remove");
        edit.setEnabled(manualPlanController != null);
        remove.setEnabled(manualPlanController != null);
        edit.addActionListener(action -> editEvent(event));
        remove.addActionListener(action -> manualPlanController.remove(event.getId()));
        actions.add(edit);
        actions.add(remove);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private void editEvent(ScheduledEvent event) {
        JTextField start = new JTextField(event.getStartTime().toString());
        JTextField end = new JTextField(event.getEndTime().toString());
        JTextField notes = new JTextField(event.getNotes());
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(new JLabel("Start time (HH:MM)"));
        form.add(start);
        form.add(new JLabel("End time (HH:MM)"));
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
