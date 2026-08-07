package closeai.adapters.views;

import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.domain.entities.Activity;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Seeded activity-discovery skeleton. Search actions are intentionally unwired. */
public final class SearchPanel extends JPanel {
    private final SearchViewModel viewModel;
    private final JPanel results = new JPanel();
    private final JScrollPane scroll;

    public SearchPanel(SearchViewModel viewModel) {
        this.viewModel = viewModel;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(searchControls(), BorderLayout.NORTH);
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBackground(SwingTheme.PANEL);
        scroll = new JScrollPane(results);
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
        if (state.getActivities().isEmpty()) {
            JLabel empty = new JLabel(state.isLoading() ? "Loading places…" : "No results");
            empty.setFont(SwingTheme.BODY);
            empty.setForeground(SwingTheme.MUTED);
            results.add(empty);
            results.revalidate();
            results.repaint();
            scroll.getVerticalScrollBar().setValue(0);
            return;
        }
        JLabel count = new JLabel(state.getActivities().size() + " seeded places");
        count.setFont(SwingTheme.SMALL);
        count.setForeground(SwingTheme.MUTED);
        results.add(count);
        results.add(Box.createVerticalStrut(8));
        String selectedId = state.getSelectedActivityId();
        List<Activity> ordered = orderSelectedFirst(state.getActivities(), selectedId);
        final JComponent[] focused = {null};
        for (Activity activity : ordered) {
            JComponent card = activityCard(activity, activity.getId().equals(selectedId));
            results.add(card);
            results.add(Box.createVerticalStrut(8));
            if (activity.getId().equals(selectedId)) focused[0] = card;
        }
        final JComponent focusedCard = focused[0];
        results.revalidate();
        results.repaint();
        if (focusedCard != null) {
            SwingUtilities.invokeLater(() -> {
                results.validate();
                scroll.getVerticalScrollBar().setValue(0);
            });
        }
    }

    /** Keeps the selected place first so it is always visible at the top of the sidebar. */
    private static List<Activity> orderSelectedFirst(List<Activity> activities, String selectedId) {
        if (selectedId == null) return new ArrayList<>(activities);
        List<Activity> ordered = new ArrayList<>();
        for (Activity activity : activities) {
            if (activity.getId().equals(selectedId)) {
                ordered.add(activity);
                break;
            }
        }
        for (Activity activity : activities) {
            if (!activity.getId().equals(selectedId)) ordered.add(activity);
        }
        return ordered;
    }

    private JComponent activityCard(Activity activity, boolean focused) {
        JPanel card = new JPanel(new BorderLayout(10, 8));
        SwingTheme.styleCard(card);
        card.putClientProperty("activityId", activity.getId());
        card.setBorder(BorderFactory.createLineBorder(
                focused ? SwingTheme.BLUE : new java.awt.Color(0, 0, 0, 0), 2));
        card.setBackground(focused ? SwingTheme.BLUE_SOFT : SwingTheme.PANEL);
        JLabel name = new JLabel(activity.getName());
        name.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        name.setForeground(SwingTheme.NAVY);
        card.add(name, BorderLayout.NORTH);
        String hoursText = activity.getOpeningHoursText();
        String hoursLine = (hoursText != null && !hoursText.trim().isEmpty())
                ? "<br><font color='#1f68e1'>Hours:</font> " + htmlEscape(hoursText)
                : "";
        JLabel details = new JLabel(String.format(
                "<html><font color='#1f68e1'>%s</font> · ★ %.1f<br>%s · %d min · %s%s</html>",
                activity.getCategory(), activity.getRating(),
                activity.getLocation().getAddress(),
                activity.getEstimatedDurationMinutes(), activity.getIndoorOutdoorType(),
                hoursLine));
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

    private static String htmlEscape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
