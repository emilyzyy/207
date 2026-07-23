package closeai.adapters.views;

import closeai.adapters.controllers.OptimizeItineraryController;
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
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Day-plan view with the milestone's single active workflow. */
public final class DayPlanPanel extends JPanel {
    private final DayPlanViewModel viewModel;
    private final OptimizeItineraryController optimizeController;
    private final JPanel eventList = new JPanel();
    private final JLabel status = new JLabel();
    private Runnable openCalendarAction = () -> { };

    public DayPlanPanel(
            DayPlanViewModel viewModel,
            OptimizeItineraryController optimizeController) {
        this.viewModel = viewModel;
        this.optimizeController = optimizeController;

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
        JButton optimize = SwingTheme.primaryButton("Optimize Current Itinerary");
        optimize.addActionListener(event -> optimizeController.execute());
        buttons.add(optimize);

        JButton calendar = new JButton("Calendar View");
        calendar.setFont(SwingTheme.BODY);
        calendar.addActionListener(event -> openCalendarAction.run());
        buttons.add(calendar);
        buttons.add(SwingTheme.placeholderButton("Edit (not wired)"));
        buttons.add(SwingTheme.placeholderButton("Remove (not wired)"));
        wrapper.add(buttons);

        JLabel notice = new JLabel(
                "Edit and remove are not wired for this milestone.");
        notice.setFont(SwingTheme.SMALL);
        notice.setForeground(SwingTheme.MUTED);
        wrapper.add(Box.createVerticalStrut(5));
        wrapper.add(notice);
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
        return card;
    }
}
