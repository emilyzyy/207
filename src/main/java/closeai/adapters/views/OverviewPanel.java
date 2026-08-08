package closeai.adapters.views;

import closeai.adapters.viewmodels.ActivitySelectionViewModel;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Color;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** Left-side interactive map and weather preview. */
public final class OverviewPanel extends JPanel {
    private final DashboardViewModel viewModel;
    private final SearchViewModel searchViewModel;
    private final BookmarksViewModel bookmarksViewModel;
    private final DayPlanViewModel dayPlanViewModel;
    private final ActivitySelectionViewModel selectionViewModel;
    private final MapPanel mapPanel;
    private final JLabel conditionLabel = new JLabel();
    private final JLabel messageLabel = new JLabel();
    private final JButton weatherPreviewButton =
            SwingTheme.secondaryButton("WEATHER PREVIEW");
    private final JLayeredPane mapLayers = new JLayeredPane();
    private HourlyForecastStrip forecastStrip;

    public OverviewPanel(DashboardViewModel viewModel, SearchViewModel searchViewModel) {
        this(viewModel, searchViewModel, null, null, null);
    }

    public OverviewPanel(DashboardViewModel viewModel, SearchViewModel searchViewModel,
                         BookmarksViewModel bookmarksViewModel,
                         DayPlanViewModel dayPlanViewModel,
                         ActivitySelectionViewModel selectionViewModel) {
        this.viewModel = viewModel;
        this.searchViewModel = searchViewModel;
        this.bookmarksViewModel = bookmarksViewModel;
        this.dayPlanViewModel = dayPlanViewModel;
        this.selectionViewModel = selectionViewModel;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 12));
        setPreferredSize(new Dimension(670, 720));

        mapPanel = new MapPanel(620, 520);
        mapPanel.setCity(viewModel.getState().getDestination());
        mapPanel.focusOnCity(viewModel.getState().getDestination());
        mapPanel.setPlaceSelectionListener(this::selectPlaceFromMap);
        mapPanel.setPlacesLoadedListener(loaded -> mergeIntoSearch(searchViewModel, loaded));
        mapPanel.setPlacesLoadingListener(loading -> {
            if (!loading && !searchViewModel.getState().getActivities().isEmpty()) return;
            searchViewModel.setLoading(loading);
        });
        // The map sits in a layered pane so the forecast strip can float over its
        // bottom-right corner instead of opening yet another window.
        mapLayers.setLayout(null);
        mapLayers.add(mapPanel, JLayeredPane.DEFAULT_LAYER);
        mapLayers.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                layOutMapLayers();
            }
        });
        add(mapLayers, BorderLayout.CENTER);
        add(weatherCard(), BorderLayout.SOUTH);

        refreshDashboard(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> refreshDashboard(viewModel.getState()));

        refreshMap();
        searchViewModel.addPropertyChangeListener(event -> refreshMap());
        if (bookmarksViewModel != null) {
            bookmarksViewModel.addPropertyChangeListener(event -> refreshMap());
        }
        if (dayPlanViewModel != null) {
            dayPlanViewModel.addPropertyChangeListener(event -> refreshMap());
        }
        if (selectionViewModel != null) {
            selectionViewModel.addPropertyChangeListener(event -> selectCurrentActivity());
        }
    }

    private JPanel weatherCard() {
        JPanel card = new JPanel(new BorderLayout(12, 3));
        SwingTheme.styleCard(card);
        JLabel icon = new JLabel("\u2600");
        icon.setFont(new Font("SansSerif", Font.PLAIN, 30));
        icon.setForeground(new Color(226, 154, 21));
        card.add(icon, BorderLayout.WEST);

        JPanel copy = new JPanel(new BorderLayout(0, 3));
        copy.setOpaque(false);
        conditionLabel.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        conditionLabel.setForeground(SwingTheme.NAVY);
        messageLabel.setFont(SwingTheme.SMALL);
        messageLabel.setForeground(SwingTheme.MUTED);
        copy.add(conditionLabel, BorderLayout.NORTH);
        copy.add(messageLabel, BorderLayout.CENTER);
        card.add(copy, BorderLayout.CENTER);

        weatherPreviewButton.setName("hourly-weather-preview");
        weatherPreviewButton.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
        weatherPreviewButton.setForeground(SwingTheme.BLUE);
        weatherPreviewButton.setToolTipText("Show or hide the hourly forecast");
        weatherPreviewButton.setEnabled(dayPlanViewModel != null);
        weatherPreviewButton.addActionListener(event -> toggleForecastStrip());
        card.add(weatherPreviewButton, BorderLayout.EAST);
        return card;
    }

    /**
     * Shows or hides the forecast strip over the map's bottom-right corner.
     *
     * <p>A toggle rather than a window: the same click that opens it closes it, Escape
     * closes it, and it never takes focus away from the dashboard. The strip is created
     * lazily on first use and kept — its view-model listener keeps it current while
     * hidden, which costs nothing and makes reopening instant.</p>
     */
    private void toggleForecastStrip() {
        if (dayPlanViewModel == null) {
            return;
        }
        if (forecastStrip == null) {
            forecastStrip = new HourlyForecastStrip(dayPlanViewModel);
            forecastStrip.setVisible(false);
            forecastStrip.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hide-forecast");
            forecastStrip.getActionMap().put("hide-forecast",
                    new javax.swing.AbstractAction() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent event) {
                            forecastStrip.setVisible(false);
                        }
                    });
            mapLayers.add(forecastStrip, JLayeredPane.PALETTE_LAYER);
        }
        forecastStrip.setVisible(!forecastStrip.isVisible());
        layOutMapLayers();
    }

    /** The map fills the layered pane; the strip hugs its bottom-right, above the bar. */
    private void layOutMapLayers() {
        int width = mapLayers.getWidth();
        int height = mapLayers.getHeight();
        mapPanel.setBounds(0, 0, width, height);
        if (forecastStrip != null) {
            int margin = 10;
            // Deliberately short: about five hours at a glance. The strip is borrowing
            // map space, and a wider one starts to feel like the panel it replaced.
            int stripWidth = Math.min(400, Math.max(260, width - 2 * margin));
            forecastStrip.setBounds(width - stripWidth - margin,
                    height - HourlyForecastStrip.STRIP_HEIGHT - margin,
                    stripWidth, HourlyForecastStrip.STRIP_HEIGHT);
        }
        mapLayers.revalidate();
        mapLayers.repaint();
    }

    private void refreshDashboard(DashboardState state) {
        conditionLabel.setText(state.getWeatherCondition());
        messageLabel.setText("<html>" + state.getWeatherMessage() + "</html>");
    }

    private void refreshMap() {
        SearchState state = searchViewModel.getState();
        Map<String, Activity> merged = new LinkedHashMap<>();
        for (Activity activity : state.getActivities()) {
            merged.put(activity.getId(), activity);
        }
        if (bookmarksViewModel != null) {
            for (Activity activity : bookmarksViewModel.getState().getBookmarks()) {
                merged.put(activity.getId(), activity);
            }
        }
        List<ScheduledEvent> events = new ArrayList<>();
        if (dayPlanViewModel != null) {
            events.addAll(dayPlanViewModel.getState().getEvents());
            for (ScheduledEvent event : events) {
                if (event.getActivity() != null) {
                    merged.put(event.getActivity().getId(), event.getActivity());
                }
            }
        }
        mapPanel.setActivities(new ArrayList<>(merged.values()));
        mapPanel.setHighlightedIds(state.getBookmarkedIds(), state.getScheduledIds());
        mapPanel.setSchedule(events);
        selectCurrentActivity();
    }

    private void selectCurrentActivity() {
        if (selectionViewModel == null) return;
        String selectedId = selectionViewModel.getSelectedActivityId();
        Activity selected = null;
        for (Activity activity : mapActivities()) {
            if (activity.getId().equals(selectedId)) {
                selected = activity;
                break;
            }
        }
        mapPanel.selectActivity(selected);
    }

    /** Keeps a map click synchronized with both the Search state and shared card selection. */
    private void selectPlaceFromMap(String activityId) {
        searchViewModel.selectActivity(activityId);
        if (selectionViewModel != null) {
            selectionViewModel.select(activityId);
            return;
        }
        for (Activity activity : mapActivities()) {
            if (activity.getId().equals(activityId)) {
                mapPanel.selectActivity(activity);
                return;
            }
        }
    }

    private List<Activity> mapActivities() {
        Map<String, Activity> activities = new LinkedHashMap<>();
        for (Activity activity : searchViewModel.getState().getActivities()) {
            activities.put(activity.getId(), activity);
        }
        if (bookmarksViewModel != null) {
            for (Activity activity : bookmarksViewModel.getState().getBookmarks()) {
                activities.put(activity.getId(), activity);
            }
        }
        if (dayPlanViewModel != null) {
            for (ScheduledEvent event : dayPlanViewModel.getState().getEvents()) {
                if (event.getActivity() != null) {
                    activities.put(event.getActivity().getId(), event.getActivity());
                }
            }
        }
        return new ArrayList<>(activities.values());
    }

    /** Folds viewport-loaded places into the shared search state so the sidebar updates too. */
    private void mergeIntoSearch(SearchViewModel searchViewModel, List<Activity> loaded) {
        if (loaded == null || loaded.isEmpty()) return;
        SearchState current = searchViewModel.getState();
        Map<String, Activity> byId = new java.util.LinkedHashMap<>();
        for (Activity activity : current.getActivities()) {
            byId.put(activity.getId(), activity);
        }
        for (Activity activity : loaded) {
            if (activity.getLocation() != null) byId.putIfAbsent(activity.getId(), activity);
        }
        searchViewModel.setState(new SearchState(
                new ArrayList<>(byId.values()),
                current.getQuery(),
                current.getBookmarkedIds(),
                current.getScheduledIds(),
                current.getSelectedActivityId(),
                current.isLoading(),
                current.getCategory(), current.getMinimumRating(),
                current.getType(), current.getFeedback()));
    }

    public MapPanel getMapPanel() {
        return mapPanel;
    }

    public JButton getWeatherPreviewButton() {
        return weatherPreviewButton;
    }

    public void setViewportPlacesLoader(MapPanel.ViewportPlacesLoader loader) {
        mapPanel.setViewportLoader(loader);
    }
}
