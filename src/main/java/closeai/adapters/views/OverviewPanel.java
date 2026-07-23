package closeai.adapters.views;

import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Left-side map placeholder and weather preview. */
public final class OverviewPanel extends JPanel {
    private final DashboardViewModel viewModel;
    private final JLabel conditionLabel = new JLabel();
    private final JLabel messageLabel = new JLabel();

    public OverviewPanel(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 12));
        setPreferredSize(new Dimension(670, 720));

        add(new MapPlaceholder(), BorderLayout.CENTER);
        add(weatherCard(), BorderLayout.SOUTH);
        refresh(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> refresh(viewModel.getState()));
    }

    private JPanel weatherCard() {
        JPanel card = new JPanel(new BorderLayout(12, 3));
        SwingTheme.styleCard(card);
        JLabel icon = new JLabel("☀");
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

    private void refresh(DashboardState state) {
        conditionLabel.setText(state.getWeatherCondition());
        messageLabel.setText("<html>" + state.getWeatherMessage() + "</html>");
    }

    private static final class MapPlaceholder extends JPanel {
        private MapPlaceholder() {
            setBackground(new Color(232, 239, 244));
            setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
            setPreferredSize(new Dimension(620, 520));
            setToolTipText("Map integration is not wired for this milestone");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D canvas = (Graphics2D) graphics.create();
            canvas.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setColor(Color.WHITE);
            canvas.setStroke(new BasicStroke(7f));
            for (int y = 70; y < getHeight(); y += 95) {
                canvas.drawLine(0, y, getWidth(), y + 55);
            }
            for (int x = 90; x < getWidth(); x += 130) {
                canvas.drawLine(x, 0, x + 40, getHeight());
            }
            canvas.setColor(new Color(183, 225, 239));
            canvas.fillRect(0, Math.max(0, getHeight() - 105), getWidth(), 105);
            drawPin(canvas, getWidth() * 36 / 100, getHeight() * 35 / 100, "1");
            drawPin(canvas, getWidth() * 57 / 100, getHeight() * 56 / 100, "2");
            drawPin(canvas, getWidth() * 46 / 100, getHeight() * 68 / 100, "3");
            canvas.setColor(SwingTheme.NAVY);
            canvas.setFont(SwingTheme.HEADING);
            canvas.drawString("Toronto overview", 24, 34);
            canvas.setFont(SwingTheme.SMALL);
            canvas.setColor(SwingTheme.MUTED);
            canvas.drawString("Map placeholder · real integration not wired", 24, 53);
            canvas.dispose();
        }

        private void drawPin(Graphics2D canvas, int x, int y, String label) {
            canvas.setColor(SwingTheme.NAVY);
            canvas.fillOval(x - 17, y - 17, 34, 34);
            canvas.setColor(Color.WHITE);
            canvas.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
            canvas.drawString(label, x - 4, y + 5);
        }
    }
}
