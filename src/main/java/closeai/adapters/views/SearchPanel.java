package closeai.adapters.views;

import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.domain.entities.Activity;
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
import javax.swing.JTextField;

/** Seeded activity-discovery skeleton. Search actions are intentionally unwired. */
public final class SearchPanel extends JPanel {
    private final SearchViewModel viewModel;
    private final JPanel results = new JPanel();

    public SearchPanel(SearchViewModel viewModel) {
        this.viewModel = viewModel;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(searchControls(), BorderLayout.NORTH);
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBackground(SwingTheme.PANEL);
        JScrollPane scroll = new JScrollPane(results);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(scroll, BorderLayout.CENTER);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
    }

    private JPanel searchControls() {
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Discover activities");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        controls.add(title);
        controls.add(Box.createVerticalStrut(8));

        JTextField search = new JTextField("Search activities (not wired)");
        search.setEnabled(false);
        search.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 38));
        controls.add(search);
        controls.add(Box.createVerticalStrut(8));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.setOpaque(false);
        for (String name : new String[]{"Food", "Museum", "Outdoor", "Shopping"}) {
            JButton filter = SwingTheme.placeholderButton(name);
            filters.add(filter);
        }
        controls.add(filters);
        JLabel notice = new JLabel("Not wired for this milestone");
        notice.setFont(SwingTheme.SMALL);
        notice.setForeground(SwingTheme.MUTED);
        controls.add(notice);
        return controls;
    }

    private void render(SearchState state) {
        results.removeAll();
        JLabel count = new JLabel(state.getActivities().size() + " seeded places");
        count.setFont(SwingTheme.SMALL);
        count.setForeground(SwingTheme.MUTED);
        results.add(count);
        results.add(Box.createVerticalStrut(8));
        for (Activity activity : state.getActivities()) {
            results.add(activityCard(activity));
            results.add(Box.createVerticalStrut(8));
        }
        results.revalidate();
        results.repaint();
    }

    private JPanel activityCard(Activity activity) {
        JPanel card = new JPanel(new BorderLayout(10, 8));
        SwingTheme.styleCard(card);
        JLabel name = new JLabel(activity.getName());
        name.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        name.setForeground(SwingTheme.NAVY);
        card.add(name, BorderLayout.NORTH);
        JLabel details = new JLabel(String.format(
                "<html><font color='#1f68e1'>%s</font> · ★ %.1f<br>%s · %d min · %s</html>",
                activity.getCategory(), activity.getRating(),
                activity.getLocation().getAddress(),
                activity.getEstimatedDurationMinutes(), activity.getIndoorOutdoorType()));
        details.setFont(SwingTheme.SMALL);
        details.setForeground(SwingTheme.MUTED);
        card.add(details, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        actions.add(SwingTheme.placeholderButton("Bookmark (not wired)"));
        actions.add(SwingTheme.placeholderButton("Add to plan (not wired)"));
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }
}
