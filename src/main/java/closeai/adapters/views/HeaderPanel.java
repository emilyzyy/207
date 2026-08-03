package closeai.adapters.views;

import closeai.adapters.controllers.ShareTripController;
import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanViewModel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Persistent application header for identity and active-trip context. */
public final class HeaderPanel extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d");

    private final DashboardViewModel viewModel;
    private final DayPlanViewModel dayPlanViewModel;
    private final JLabel tripLabel = new JLabel();
    private final JLabel dateLabel = new JLabel();
    private final JButton shareButton = SwingTheme.primaryButton("Share");
    private Runnable openShareAction = () -> { };
    private Runnable onHomeAction = () -> { };

    public HeaderPanel(
            DashboardViewModel viewModel,
            DayPlanViewModel dayPlanViewModel,
            ShareTripController shareController) {
        if (viewModel == null || dayPlanViewModel == null || shareController == null) {
            throw new IllegalArgumentException("Header dependencies are required");
        }
        this.viewModel = viewModel;
        this.dayPlanViewModel = dayPlanViewModel;
        setLayout(new BorderLayout(24, 0));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SwingTheme.LINE),
                BorderFactory.createEmptyBorder(13, 22, 13, 22)));

        JLabel brand = new JLabel("CloseAI");
        brand.setFont(SwingTheme.TITLE);
        brand.setForeground(SwingTheme.NAVY);
        brand.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        brand.setToolTipText("Back to My Trips");
        brand.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                brand.setForeground(SwingTheme.BLUE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                brand.setForeground(SwingTheme.NAVY);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                onHomeAction.run();
            }
        });
        add(brand, BorderLayout.WEST);

        JPanel tripSummary = new JPanel();
        tripSummary.setOpaque(false);
        tripSummary.setLayout(new BoxLayout(tripSummary, BoxLayout.Y_AXIS));
        tripLabel.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
        tripLabel.setForeground(SwingTheme.NAVY);
        dateLabel.setFont(SwingTheme.SMALL);
        dateLabel.setForeground(SwingTheme.MUTED);
        tripSummary.add(tripLabel);
        tripSummary.add(Box.createVerticalStrut(3));
        tripSummary.add(dateLabel);
        add(tripSummary, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        shareButton.setName("share-trip");
        shareButton.addActionListener(event -> {
            shareController.execute();
            openShareAction.run();
        });
        actions.add(shareButton);
        add(actions, BorderLayout.EAST);

        refresh(viewModel.getState());
        refreshShareAvailability();
        viewModel.addPropertyChangeListener(event -> refresh(viewModel.getState()));
        dayPlanViewModel.addPropertyChangeListener(event -> refreshShareAvailability());
    }

    public void setOpenShareAction(Runnable action) {
        openShareAction = action == null ? () -> { } : action;
    }

    public void setOnHomeAction(Runnable onHomeAction) {
        this.onHomeAction = onHomeAction;
    }

    private void refresh(DashboardState state) {
        tripLabel.setText(state.getDestination().isEmpty()
                ? "Create a trip to begin" : state.getDestination() + " day trip");
        dateLabel.setText(state.getDate() == null
                ? "Date not selected" : DATE.format(state.getDate()));
    }

    private void refreshShareAvailability() {
        shareButton.setEnabled(!dayPlanViewModel.getState().getTripId().isEmpty());
        shareButton.setToolTipText(shareButton.isEnabled()
                ? "Preview and copy this itinerary"
                : "Create a trip before sharing");
    }
}
