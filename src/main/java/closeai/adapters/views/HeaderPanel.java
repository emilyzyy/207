package closeai.adapters.views;

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

/** Persistent application header for identity and active-trip context. */
public final class HeaderPanel extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d");

    private final DashboardViewModel viewModel;
    private final JLabel tripLabel = new JLabel();
    private final JLabel dateLabel = new JLabel();

    public HeaderPanel(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
        setLayout(new BorderLayout(24, 0));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SwingTheme.LINE),
                BorderFactory.createEmptyBorder(13, 22, 13, 22)));

        JLabel brand = new JLabel("CloseAI");
        brand.setFont(SwingTheme.TITLE);
        brand.setForeground(SwingTheme.NAVY);
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
        JButton share = SwingTheme.placeholderButton("Share (not wired)");
        actions.add(share);
        add(actions, BorderLayout.EAST);

        refresh(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> refresh(viewModel.getState()));
    }

    private void refresh(DashboardState state) {
        tripLabel.setText(state.getDestination() + " day trip");
        dateLabel.setText(state.getDate() == null
                ? "Date not selected" : DATE.format(state.getDate()));
    }
}
