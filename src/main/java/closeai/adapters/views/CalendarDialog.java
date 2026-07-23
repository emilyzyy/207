package closeai.adapters.views;

import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.domain.entities.ScheduledEvent;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Modeless calendar owned by the main frame and backed by the shared Day Plan state. */
public final class CalendarDialog extends JDialog {
    private final DayPlanViewModel viewModel;
    private final JPanel timeline = new JPanel();
    private final JLabel summary = new JLabel();

    public CalendarDialog(Frame owner, DayPlanViewModel viewModel) {
        super(owner, "Calendar · Day Plan", false);
        this.viewModel = viewModel;
        setSize(620, 560);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 12));
        getContentPane().setBackground(SwingTheme.BACKGROUND);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setBackground(SwingTheme.PANEL);
        heading.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel title = new JLabel("Calendar");
        title.setFont(SwingTheme.TITLE);
        title.setForeground(SwingTheme.NAVY);
        heading.add(title, BorderLayout.WEST);
        summary.setFont(SwingTheme.SMALL);
        summary.setForeground(SwingTheme.MUTED);
        heading.add(summary, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        timeline.setLayout(new BoxLayout(timeline, BoxLayout.Y_AXIS));
        timeline.setBackground(SwingTheme.BACKGROUND);
        timeline.setBorder(BorderFactory.createEmptyBorder(10, 16, 16, 16));
        JScrollPane scroll = new JScrollPane(timeline);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
    }

    /** Exposes identity for composition tests without creating a second state source. */
    public DayPlanViewModel getViewModel() {
        return viewModel;
    }

    private void render(DayPlanState state) {
        timeline.removeAll();
        summary.setText(state.getEvents().size() + " scheduled item(s)");
        if (state.getEvents().isEmpty()) {
            JLabel empty = new JLabel("No scheduled activities.");
            empty.setFont(SwingTheme.BODY);
            empty.setForeground(SwingTheme.MUTED);
            timeline.add(empty);
        } else {
            for (ScheduledEvent event : state.getEvents()) {
                timeline.add(calendarEntry(event));
                timeline.add(Box.createVerticalStrut(9));
            }
        }
        timeline.revalidate();
        timeline.repaint();
    }

    private JPanel calendarEntry(ScheduledEvent event) {
        JPanel entry = new JPanel(new BorderLayout(14, 4));
        SwingTheme.styleCard(entry);
        JLabel time = new JLabel(event.getStartTime() + " – " + event.getEndTime());
        time.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        time.setForeground(SwingTheme.BLUE);
        entry.add(time, BorderLayout.WEST);
        String name = event.getActivity() == null
                ? event.getEventType().toString()
                : event.getActivity().getName();
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        nameLabel.setForeground(SwingTheme.NAVY);
        entry.add(nameLabel, BorderLayout.CENTER);
        return entry;
    }
}
