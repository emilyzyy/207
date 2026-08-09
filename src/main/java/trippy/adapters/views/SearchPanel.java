package trippy.adapters.views;

import trippy.adapters.controllers.ActivityDiscoveryController;
import trippy.adapters.controllers.BookmarkController;
import trippy.adapters.controllers.ManualPlanController;
import trippy.adapters.viewmodels.ActivitySelectionViewModel;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.adapters.viewmodels.SearchState;
import trippy.adapters.viewmodels.SearchViewModel;
import trippy.adapters.viewmodels.TripAccessViewModel;
import trippy.adapters.viewmodels.TripOptionsViewModel;
import trippy.domain.entities.Activity;
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.IndoorOutdoorType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.Rectangle;

/** Activity discovery view backed by application-layer search, filter, and bookmark use cases. */
public final class SearchPanel extends JPanel {
    private final SearchViewModel viewModel;
    private final ActivityDiscoveryController discovery;
    private final BookmarkController bookmarks;
    private final ManualPlanController manualPlan;
    private final ActivitySelectionViewModel selection;
    private final DayPlanViewModel dayPlan;
    private final TripOptionsViewModel tripOptions;
    private TripAccessViewModel tripAccess;
    private final JPanel results = new JPanel();
    private final JScrollPane scroll;
    private final JTextField search = new JTextField();
    private final JComboBox<String> category = new JComboBox<>(new String[]{
        "All categories", "Food", "Museum", "Shopping", "Coffee", "Attraction",
        "Entertainment", "Parks/Nature", "Historic", "Sports/Recreation", "Arts/Culture"
    });
    private final JComboBox<String> type = new JComboBox<>(new String[]{
        "Any setting", "Indoor", "Outdoor"
    });
    private final JButton searchButton = SwingTheme.primaryButton("Search");
    private final JLabel feedback = new JLabel(" ");

    public SearchPanel(SearchViewModel viewModel) {
        this(viewModel, null, null, null, null, null, null, null);
    }

    public SearchPanel(SearchViewModel viewModel, ActivityDiscoveryController discovery,
                       BookmarkController bookmarks) {
        this(viewModel, discovery, bookmarks, null, null, null, null, null);
    }

    public SearchPanel(SearchViewModel viewModel, ActivityDiscoveryController discovery,
                       BookmarkController bookmarks, ManualPlanController manualPlan) {
        this(viewModel, discovery, bookmarks, manualPlan, null, null, null, null);
    }

    public SearchPanel(SearchViewModel viewModel, ActivityDiscoveryController discovery,
                       BookmarkController bookmarks, ManualPlanController manualPlan,
                       ActivitySelectionViewModel selection) {
        this(viewModel, discovery, bookmarks, manualPlan, selection, null, null, null);
    }

    public SearchPanel(SearchViewModel viewModel, ActivityDiscoveryController discovery,
                       BookmarkController bookmarks, ManualPlanController manualPlan,
                       ActivitySelectionViewModel selection,
                       DayPlanViewModel dayPlan,
                       TripOptionsViewModel tripOptions) {
        this(viewModel, discovery, bookmarks, manualPlan, selection, dayPlan, tripOptions, null);
    }

    public SearchPanel(SearchViewModel viewModel, ActivityDiscoveryController discovery,
                       BookmarkController bookmarks, ManualPlanController manualPlan,
                       ActivitySelectionViewModel selection,
                       TripAccessViewModel tripAccess) {
        this(viewModel, discovery, bookmarks, manualPlan, selection, null, null, tripAccess);
    }

    public SearchPanel(SearchViewModel viewModel, ActivityDiscoveryController discovery,
                       BookmarkController bookmarks, ManualPlanController manualPlan,
                       ActivitySelectionViewModel selection,
                       DayPlanViewModel dayPlan,
                       TripOptionsViewModel tripOptions,
                       TripAccessViewModel tripAccess) {
        this.viewModel = viewModel;
        this.discovery = discovery;
        this.bookmarks = bookmarks;
        this.manualPlan = manualPlan;
        this.selection = selection;
        this.dayPlan = dayPlan;
        this.tripOptions = tripOptions;
        this.tripAccess = tripAccess;
        SwingTheme.styleComboBox(category);
        SwingTheme.styleComboBox(type);
        category.setRenderer(new CategoryFilterRenderer());
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(searchControls(), BorderLayout.NORTH);
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBackground(SwingTheme.BACKGROUND);
        scroll = new JScrollPane(results);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SwingTheme.BACKGROUND);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(scroll, BorderLayout.CENTER);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
        if (selection != null) {
            selection.addPropertyChangeListener(event -> render(viewModel.getState()));
        }
        if (tripAccess != null) {
            tripAccess.addPropertyChangeListener(event -> render(viewModel.getState()));
        }
    }

    private boolean canEditItinerary() {
        return tripAccess == null || tripAccess.canEditItinerary();
    }

    private JPanel searchControls() {
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel titleLabel = new JLabel("Discover activities");
        titleLabel.setFont(SwingTheme.HEADING);
        titleLabel.setForeground(SwingTheme.NAVY);
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        titleRow.add(titleLabel, BorderLayout.CENTER);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                titleRow.getPreferredSize().height));
        controls.add(titleRow);
        controls.add(Box.createVerticalStrut(10));

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.setAlignmentX(LEFT_ALIGNMENT);
        search.setToolTipText("Search by activity name or category");
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        search.setAlignmentX(LEFT_ALIGNMENT);
        search.addActionListener(event -> runDiscovery());
        searchRow.add(search, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                Math.max(search.getPreferredSize().height,
                        searchButton.getPreferredSize().height)));
        controls.add(searchRow);
        controls.add(Box.createVerticalStrut(4));

        JPanel feedbackRow = new JPanel(new BorderLayout());
        feedbackRow.setOpaque(false);
        feedbackRow.setAlignmentX(LEFT_ALIGNMENT);
        feedback.setFont(SwingTheme.SMALL);
        feedback.setForeground(SwingTheme.MUTED);
        feedback.setHorizontalAlignment(JLabel.CENTER);
        feedbackRow.add(feedback, BorderLayout.CENTER);
        feedbackRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                feedbackRow.getPreferredSize().height));
        controls.add(feedbackRow);
        controls.add(Box.createVerticalStrut(8));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.setOpaque(false);
        filters.add(category);
        filters.add(type);
        searchButton.addActionListener(event -> runDiscovery());
        category.addActionListener(event -> runDiscovery());
        type.addActionListener(event -> runDiscovery());
        filters.setAlignmentX(LEFT_ALIGNMENT);
        controls.add(filters);
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
                discovery.execute(search.getText(), selectedCategory(), 0.0, selectedType());
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
                : categoryForLabel((String) category.getSelectedItem());
    }

    private static ActivityCategory categoryForLabel(String label) {
        if (label == null || "All categories".equals(label)) return null;
        return ActivityCategory.valueOf(label.toUpperCase().replace('/', '_'));
    }

    /** Gives each category choice the same surface colour as its activity cards. */
    static final class CategoryFilterRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected,
                                                       boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            ActivityCategory activityCategory = categoryForLabel(
                    value == null ? null : value.toString());
            Color surface = activityCategory == null
                    ? SwingTheme.PANEL : SwingTheme.categorySurface(activityCategory);
            label.setBackground(surface);
            label.setForeground(list.isEnabled() ? SwingTheme.NAVY : SwingTheme.MUTED);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createCompoundBorder(
                    isSelected
                            ? BorderFactory.createLineBorder(SwingTheme.BLUE, 2)
                            : BorderFactory.createEmptyBorder(2, 2, 2, 2),
                    BorderFactory.createEmptyBorder(4, 7, 4, 7)));
            return label;
        }
    }

    private IndoorOutdoorType selectedType() {
        return type.getSelectedIndex() == 0 ? null
                : IndoorOutdoorType.valueOf(((String) type.getSelectedItem()).toUpperCase());
    }

    private void setControlsEnabled(boolean enabled) {
        search.setEnabled(enabled);
        category.setEnabled(enabled);
        type.setEnabled(enabled);
        searchButton.setEnabled(enabled);
    }

    private void render(SearchState state) {
        feedback.setText(state.getFeedback().isEmpty()
                ? state.getActivities().size() + " nearby activities" : state.getFeedback());
        results.removeAll();
        if (state.getActivities().isEmpty()) {
            JLabel empty = new JLabel(state.isLoading()
                    ? "Loading places…"
                    : "No activities match your search and filters");
            empty.setFont(SwingTheme.BODY);
            empty.setForeground(SwingTheme.MUTED);
            results.add(empty);
            results.revalidate();
            results.repaint();
            scroll.getVerticalScrollBar().setValue(0);
            return;
        }
        String selectedId = selection == null
                ? state.getSelectedActivityId() : selection.getSelectedActivityId();
        List<Activity> ordered = orderSelectedFirst(state.getActivities(), selectedId);
        final JComponent[] focused = {null};
        for (Activity activity : ordered) {
            JComponent card = activityCard(activity, state, activity.getId().equals(selectedId));
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

    private JComponent activityCard(Activity activity, SearchState state, boolean focused) {
        JPanel card = new JPanel(new BorderLayout(10, 8));
        SwingTheme.styleCard(card);
        card.setPreferredSize(new Dimension(10, 132));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
        card.setMinimumSize(new Dimension(0, 132));
        card.setBackground(SwingTheme.categorySurface(activity.getCategory()));
        card.putClientProperty("activityId", activity.getId());
        makeSelectable(card, activity, focused);
        JLabel name = new JLabel(activity.getName());
        name.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        name.setForeground(SwingTheme.NAVY);
        card.add(name, BorderLayout.NORTH);
        String hoursText = activity.getOpeningHoursText();
        boolean hasHours = hoursText != null && !hoursText.trim().isEmpty();
        String hoursLine = "<br><font color='#1f68e1'>Hours:</font> "
                + (hasHours ? htmlEscape(hoursText) : "Not on record");
        JLabel details = new JLabel(String.format(
                "<html><font color='#1f68e1'>%s</font><br>%s - %d min - %s%s</html>",
                categoryLabel(activity.getCategory()),
                activity.getLocation().getAddress(),
                activity.getEstimatedDurationMinutes(), activity.getIndoorOutdoorType(),
                hoursLine));
        details.setFont(SwingTheme.SMALL);
        details.setForeground(SwingTheme.MUTED);
        card.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        boolean saved = state.getBookmarkedIds().contains(activity.getId());
        JButton bookmarkButton = saved
                ? SwingTheme.secondaryButton("Remove bookmark")
                : SwingTheme.primaryButton("Bookmark");
        bookmarkButton.setEnabled(bookmarks != null && canEditItinerary());
        bookmarkButton.addActionListener(event -> {
            if (canEditItinerary()) {
                bookmarks.toggle(activity.getId());
            }
        });
        actions.add(bookmarkButton);
        boolean planned = state.getScheduledIds().contains(activity.getId());
        if (planned) {
            JLabel plannedLabel = new JLabel("In day plan");
            plannedLabel.setFont(SwingTheme.SMALL);
            plannedLabel.setForeground(SwingTheme.MUTED);
            actions.add(plannedLabel);
        } else {
            JButton add = SwingTheme.primaryButton("Add to plan");
            add.setEnabled(manualPlan != null && canEditItinerary());
            if (!canEditItinerary()) {
                add.setToolTipText("View only — you cannot change this itinerary");
            }
            add.addActionListener(event -> {
                if (canEditItinerary()) {
                    addToPlan(activity);
                }
            });
            actions.add(add);
        }
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    /** BoxLayout content that always tracks the viewport width, preventing lateral growth. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation,
                                                         int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation,
                                                          int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private void makeSelectable(JPanel card, Activity activity, boolean focused) {
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText("Show " + activity.getName() + " on the map");
        card.setBorder(BorderFactory.createLineBorder(
                focused ? SwingTheme.BLUE : new java.awt.Color(0, 0, 0, 0), 2, true));
        card.setBackground(SwingTheme.categorySurface(activity.getCategory()));
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (selection != null) {
                    selection.select(activity.getId());
                }
            }
        });
    }

    private static String htmlEscape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String categoryLabel(ActivityCategory category) {
        switch (category) {
            case PARKS_NATURE: return "PARKS / NATURE";
            case SPORTS_RECREATION: return "SPORTS / RECREATION";
            case ARTS_CULTURE: return "ARTS / CULTURE";
            default: return category.toString();
        }
    }

    private void addToPlan(Activity activity) {
        if (dayPlan != null && tripOptions != null) {
            AddToPlanDialog.open(this, activity, dayPlan, tripOptions, manualPlan);
        } else {
            manualPlan.add(activity.getId(), "");
        }
    }
}
