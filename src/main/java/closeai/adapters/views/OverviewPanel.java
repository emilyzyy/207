package closeai.adapters.views;

import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Left-side interactive map and weather preview. */
public final class OverviewPanel extends JPanel {
    private final DashboardViewModel viewModel;
    private final MapPanel mapPanel;
    private final JLabel conditionLabel = new JLabel();
    private final JLabel messageLabel = new JLabel();

    public OverviewPanel(DashboardViewModel viewModel, SearchViewModel searchViewModel) {
        this.viewModel = viewModel;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 12));
        setPreferredSize(new Dimension(670, 720));

        mapPanel = new MapPanel(620, 520);
        add(mapPanel, BorderLayout.CENTER);
        add(weatherCard(), BorderLayout.SOUTH);

        refreshDashboard(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> refreshDashboard(viewModel.getState()));

        updateMap(searchViewModel.getState());
        searchViewModel.addPropertyChangeListener(event -> updateMap(searchViewModel.getState()));
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

        JLabel preview = new JLabel("WEATHER PREVIEW");
        preview.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
        preview.setForeground(SwingTheme.BLUE);
        card.add(preview, BorderLayout.EAST);
        return card;
    }

    private void refreshDashboard(DashboardState state) {
        conditionLabel.setText(state.getWeatherCondition());
        messageLabel.setText("<html>" + state.getWeatherMessage() + "</html>");
    }

    private void updateMap(SearchState state) {
        mapPanel.setActivities(state.getActivities());
    }

    public MapPanel getMapPanel() {
        return mapPanel;
    }
}
