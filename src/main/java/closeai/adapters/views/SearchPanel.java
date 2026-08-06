package closeai.adapters.views;

import closeai.adapters.controllers.ActivityDiscoveryController;
import closeai.adapters.controllers.BookmarkController;
import closeai.adapters.controllers.ManualPlanController;
import closeai.adapters.viewmodels.ActivitySelectionViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/** Activity discovery view backed by application-layer search, filter, and bookmark use cases. */
public final class SearchPanel extends JPanel {
    private final SearchViewModel viewModel;
    private final ActivityDiscoveryController discovery;
    private final BookmarkController bookmarks;
    private final ManualPlanController manualPlan;
    private final ActivitySelectionViewModel selection;
    private final JPanel results = new JPanel();
    private final JTextField search = new JTextField();
    private final JComboBox<String> category = new JComboBox<>(new String[]{
        "All categories", "Food", "Museum", "Outdoor", "Shopping", "Coffee", "Attraction"
    });
    private final JComboBox<String> rating = new JComboBox<>(new String[]{
        "Any rating", "4.0+", "4.5+"
    });
    private final JComboBox<String> type = new JComboBox<>(new String[]{
        "Any setting", "Indoor", "Outdoor", "Mixed"
    });
    private final JButton searchButton = SwingTheme.primaryButton("Search");
    private final JLabel feedback = new JLabel(" ");

    public SearchPanel(SearchViewModel viewModel) {
        this(viewModel, null, null, null, null);
    }

    public SearchPanel(SearchViewModel viewModel, ActivityDiscoveryController discovery,
                       BookmarkController bookmarks) {
        this(viewModel, discovery, bookmarks, null, null);
    }

    public SearchPanel(SearchViewModel viewModel, ActivityDiscoveryController discovery,
                       BookmarkController bookmarks, ManualPlanController manualPlan) {
        this(viewModel, discovery, bookmarks, manualPlan, null);
    }

    public SearchPanel(SearchViewModel viewModel, ActivityDiscoveryController discovery,
                       BookmarkController bookmarks, ManualPlanController manualPlan,
                       ActivitySelectionViewModel selection) {
        this.viewModel = viewModel;
        this.discovery = discovery;
        this.bookmarks = bookmarks;
        this.manualPlan = manualPlan;
        this.selection = selection;
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
        if (selection != null) {
            selection.addPropertyChangeListener(event -> render(viewModel.getState()));
        }
    }

    private JPanel searchControls() {
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel("Discover activities");
        titleLabel.setFont(SwingTheme.HEADING);
        titleLabel.setForeground(SwingTheme.NAVY);
        controls.add(titleLabel);
        controls.add(Box.createVerticalStrut(8));

        search.setToolTipText("Search by activity name or category");
        search.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 38));
        search.addActionListener(event -> runDiscovery());
        controls.add(search);
        controls.add(Box.createVerticalStrut(8));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.setOpaque(false);
        filters.add(category);
        filters.add(rating);
        filters.add(type);
        filters.add(searchButton);
        searchButton.addActionListener(event -> runDiscovery());
        category.addActionListener(event -> runDiscovery());
        rating.addActionListener(event -> runDiscovery());
        type.addActionListener(event -> runDiscovery());
        controls.add(filters);

        feedback.setFont(SwingTheme.SMALL);
        feedback.setForeground(SwingTheme.MUTED);
        controls.add(feedback);
        setControlsEnabled(discovery != null);
        return controls;
    }

    private void runDiscovery() {
        if (discovery == null || !searchButton.isEnabled()) {
            return;
        }
        setControlsEnabled(false);
        feedback.setText("Searching nearby activities...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                discovery.execute(search.getText(), selectedCategory(), selectedRating(), selectedType());
                return null;
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
            }
        }.execute();
    }

    private ActivityCategory selectedCategory() {
        return category.getSelectedIndex() == 0 ? null
                : ActivityCategory.valueOf(((String) category.getSelectedItem()).toUpperCase());
    }

    private double selectedRating() {
        switch (rating.getSelectedIndex()) {
            case 1: return 4.0;
            case 2: return 4.5;
            default: return 0.0;
        }
    }

    private IndoorOutdoorType selectedType() {
        return type.getSelectedIndex() == 0 ? null
                : IndoorOutdoorType.valueOf(((String) type.getSelectedItem()).toUpperCase());
    }

    private void setControlsEnabled(boolean enabled) {
        search.setEnabled(enabled);
        category.setEnabled(enabled);
        rating.setEnabled(enabled);
        type.setEnabled(enabled);
        searchButton.setEnabled(enabled);
    }

    private void render(SearchState state) {
        feedback.setText(state.getFeedback().isEmpty()
                ? state.getActivities().size() + " nearby activities" : state.getFeedback());
        results.removeAll();
        if (state.getActivities().isEmpty()) {
            JLabel empty = new JLabel("No activities match your search and filters");
            empty.setFont(SwingTheme.BODY);
            empty.setForeground(SwingTheme.MUTED);
            results.add(empty);
        } else {
            for (Activity activity : state.getActivities()) {
                results.add(activityCard(activity, state));
                results.add(Box.createVerticalStrut(8));
            }
        }
        results.revalidate();
        results.repaint();
    }

    private JPanel activityCard(Activity activity, SearchState state) {
        JPanel card = new JPanel(new BorderLayout(10, 8));
        SwingTheme.styleCard(card);
        makeSelectable(card, activity);
        JLabel name = new JLabel(activity.getName());
        name.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        name.setForeground(SwingTheme.NAVY);
        card.add(name, BorderLayout.NORTH);
        JLabel details = new JLabel(String.format(
                "<html><font color='#1f68e1'>%s</font> - &#9733; %.1f<br>%s - %d min - %s</html>",
                activity.getCategory(), activity.getRating(), activity.getLocation().getAddress(),
                activity.getEstimatedDurationMinutes(), activity.getIndoorOutdoorType()));
        details.setFont(SwingTheme.SMALL);
        details.setForeground(SwingTheme.MUTED);
        card.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        boolean saved = state.getBookmarkedIds().contains(activity.getId());
        JButton bookmarkButton = saved
                ? SwingTheme.secondaryButton("Remove bookmark")
                : SwingTheme.primaryButton("Bookmark");
        bookmarkButton.setEnabled(bookmarks != null);
        bookmarkButton.addActionListener(event -> bookmarks.toggle(activity.getId()));
        actions.add(bookmarkButton);
        boolean planned = state.getScheduledIds().contains(activity.getId());
        if (planned) {
            JLabel plannedLabel = new JLabel("In day plan");
            plannedLabel.setFont(SwingTheme.SMALL);
            plannedLabel.setForeground(SwingTheme.MUTED);
            actions.add(plannedLabel);
        } else {
            JButton add = SwingTheme.primaryButton("Add to plan");
            add.setEnabled(manualPlan != null);
            add.addActionListener(event -> addToPlan(activity));
            actions.add(add);
        }
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void makeSelectable(JPanel card, Activity activity) {
        if (selection == null) return;
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText("Show " + activity.getName() + " on the map");
        if (activity.getId().equals(selection.getSelectedActivityId())) {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SwingTheme.BLUE, 2),
                    BorderFactory.createEmptyBorder(11, 13, 11, 13)));
        }
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                selection.select(activity.getId());
            }
        });
    }

    private void addToPlan(Activity activity) {
        String start = JOptionPane.showInputDialog(
                this,
                "Preferred start time for " + activity.getName()
                        + " (HH:MM). Leave blank for the next available time.",
                "Add activity to Day Plan",
                JOptionPane.PLAIN_MESSAGE);
        if (start != null) {
            manualPlan.add(activity.getId(), start);
        }
    }
}
