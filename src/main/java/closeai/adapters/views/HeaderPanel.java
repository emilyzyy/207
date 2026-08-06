package closeai.adapters.views;

import closeai.adapters.controllers.ShareItineraryController;
import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Persistent application header for identity and active-trip context. */
public final class HeaderPanel extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d");
    private static final int TOAST_MS = 3000;

    private final DashboardViewModel viewModel;
    private final ShareItineraryController shareController;
    private final JLabel tripLabel = new JLabel();
    private final JLabel dateLabel = new JLabel();
    private final JButton shareButton = SwingTheme.primaryButton("Share PNG");
    private final JLabel toastLabel = new JLabel(" ");
    private Timer toastTimer;

    public HeaderPanel(DashboardViewModel viewModel, ShareItineraryController shareController) {
        if (viewModel == null || shareController == null) {
            throw new IllegalArgumentException("Header dependencies are required");
        }
        this.viewModel = viewModel;
        this.shareController = shareController;
        setLayout(new BorderLayout());
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SwingTheme.LINE),
                BorderFactory.createEmptyBorder(13, 22, 8, 22)));

        JPanel mainRow = new JPanel(new BorderLayout(24, 0));
        mainRow.setOpaque(false);

        JLabel brand = new JLabel("CloseAI");
        brand.setFont(SwingTheme.TITLE);
        brand.setForeground(SwingTheme.NAVY);
        mainRow.add(brand, BorderLayout.WEST);

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
        mainRow.add(tripSummary, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        shareButton.addActionListener(event -> shareController.execute());
        shareButton.setVisible(false);
        actions.add(shareButton);
        mainRow.add(actions, BorderLayout.EAST);

        toastLabel.setFont(SwingTheme.BODY);
        toastLabel.setForeground(SwingTheme.ERROR);
        toastLabel.setVisible(false);
        toastLabel.setHorizontalAlignment(JLabel.CENTER);
        toastLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        add(mainRow, BorderLayout.CENTER);
        add(toastLabel, BorderLayout.SOUTH);

        refresh(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> refresh(viewModel.getState()));
    }

    /** Shows red error text under the header for a few seconds, then clears it. */
    public void showErrorToast(String message) {
        String text = message == null || message.trim().isEmpty()
                ? "Unable to share itinerary" : message.trim();
        if (toastTimer != null && toastTimer.isRunning()) {
            toastTimer.stop();
        }
        toastLabel.setText(text);
        toastLabel.setVisible(true);
        revalidate();
        repaint();
        toastTimer = new Timer(TOAST_MS, event -> {
            toastLabel.setText(" ");
            toastLabel.setVisible(false);
            revalidate();
            repaint();
        });
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    private void refresh(DashboardState state) {
        boolean hasTrip = state.getDestination() != null && !state.getDestination().isEmpty();
        tripLabel.setText(hasTrip
                ? state.getDestination() + " day trip" : "Create a trip to begin");
        dateLabel.setText(state.getDate() == null
                ? "Date not selected" : DATE.format(state.getDate()));
        shareButton.setVisible(hasTrip);
        shareButton.setToolTipText(
                "Export the current itinerary as a PNG to share with friends");
    }
}
