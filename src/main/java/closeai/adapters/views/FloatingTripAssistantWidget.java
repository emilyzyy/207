package closeai.adapters.views;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLayeredPane;

/** Keeps George available above every planner tab without creating another window. */
public final class FloatingTripAssistantWidget extends JLayeredPane {
    static final int AVATAR_SIZE = 64;
    static final int EDGE_GAP = 20;
    static final int PANEL_GAP = 10;
    static final int PREFERRED_PANEL_WIDTH = 400;
    static final int PREFERRED_PANEL_HEIGHT = 500;

    private final Component content;
    private final TripAssistantPanel assistantPanel;
    private final JButton avatarButton = new CircularAvatarButton();
    private boolean expanded;

    public FloatingTripAssistantWidget(
            Component content, TripAssistantPanel assistantPanel) {
        if (content == null || assistantPanel == null) {
            throw new IllegalArgumentException("Floating assistant dependencies are required");
        }
        this.content = content;
        this.assistantPanel = assistantPanel;
        setLayout(null);
        setOpaque(true);
        setBackground(SwingTheme.BACKGROUND);
        add(content, DEFAULT_LAYER);
        add(assistantPanel, PALETTE_LAYER);
        add(avatarButton, MODAL_LAYER);

        assistantPanel.setVisible(false);
        assistantPanel.setCollapseAction(() -> setExpanded(false));
        avatarButton.addActionListener(event -> setExpanded(!expanded));
    }

    @Override
    public void doLayout() {
        content.setBounds(0, 0, getWidth(), getHeight());

        int avatarWidth = Math.min(AVATAR_SIZE, Math.max(0, getWidth()));
        int avatarHeight = Math.min(AVATAR_SIZE, Math.max(0, getHeight()));
        int avatarX = Math.max(0, getWidth() - EDGE_GAP - avatarWidth);
        int avatarY = Math.max(0, getHeight() - EDGE_GAP - avatarHeight);
        avatarButton.setBounds(avatarX, avatarY, avatarWidth, avatarHeight);

        int availableWidth = Math.max(0, getWidth() - 2 * EDGE_GAP);
        int availableHeight = Math.max(0, avatarY - PANEL_GAP - EDGE_GAP);
        int panelWidth = Math.min(PREFERRED_PANEL_WIDTH, availableWidth);
        int panelHeight = Math.min(PREFERRED_PANEL_HEIGHT, availableHeight);
        int panelX = Math.max(0, getWidth() - EDGE_GAP - panelWidth);
        int panelY = Math.max(0, avatarY - PANEL_GAP - panelHeight);
        assistantPanel.setBounds(panelX, panelY, panelWidth, panelHeight);
    }

    @Override
    public Dimension getPreferredSize() {
        return content.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return content.getMinimumSize();
    }

    public void setExpanded(boolean value) {
        if (expanded == value) {
            return;
        }
        expanded = value;
        assistantPanel.setVisible(expanded);
        avatarButton.setToolTipText(expanded
                ? "Collapse George chat" : "Open George chat");
        avatarButton.getAccessibleContext().setAccessibleName(expanded
                ? "Collapse George chat" : "Open George chat");
        revalidate();
        doLayout();
        repaint();
        if (expanded) {
            assistantPanel.getInputField().requestFocusInWindow();
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public JButton getAvatarButton() {
        return avatarButton;
    }

    public TripAssistantPanel getAssistantPanel() {
        return assistantPanel;
    }

    /** Circular button with a quiet shadow so George remains legible over the map. */
    private static final class CircularAvatarButton extends JButton {
        private CircularAvatarButton() {
            super(GeorgeAvatar.icon(48, 48));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setToolTipText("Open George chat");
            getAccessibleContext().setAccessibleName("Open George chat");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = Math.max(0, Math.min(getWidth(), getHeight()) - 6);
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            copy.setColor(new Color(13, 35, 64, 55));
            copy.fillOval(x + 2, y + 3, diameter, diameter);
            copy.setColor(SwingTheme.PANEL);
            copy.fillOval(x, y, diameter, diameter);
            copy.setColor(SwingTheme.BLUE);
            copy.drawOval(x, y, Math.max(0, diameter - 1), Math.max(0, diameter - 1));

            Shape oldClip = copy.getClip();
            copy.clip(new Ellipse2D.Double(x + 5, y + 5,
                    Math.max(0, diameter - 10), Math.max(0, diameter - 10)));
            super.paintComponent(copy);
            copy.setClip(oldClip);
            copy.dispose();
        }

        @Override
        public boolean contains(int x, int y) {
            double radius = Math.min(getWidth(), getHeight()) / 2.0;
            double dx = x - getWidth() / 2.0;
            double dy = y - getHeight() / 2.0;
            return dx * dx + dy * dy <= radius * radius;
        }
    }
}
