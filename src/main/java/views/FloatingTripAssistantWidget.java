package views;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JLayeredPane;

/** Keeps George available above every planner tab without creating another window. */
public final class FloatingTripAssistantWidget extends JLayeredPane {
    static final int AVATAR_SIZE = 64;
    static final int EDGE_GAP = 20;
    /** Clears the planner's bottom action row, including Calendar View. */
    static final int AVATAR_BOTTOM_GAP = 76;
    static final int PANEL_GAP = 10;
    static final int PREFERRED_PANEL_WIDTH = 400;
    static final int PREFERRED_PANEL_HEIGHT = 500;

    private final Component content;
    private final TripAssistantPanel assistantPanel;
    private final JButton avatarButton = new CircularAvatarButton();
    private final JPanel greeting = createGreeting();
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
        add(greeting, PALETTE_LAYER);
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
        int avatarY = Math.max(0, getHeight() - AVATAR_BOTTOM_GAP - avatarHeight);
        avatarButton.setBounds(avatarX, avatarY, avatarWidth, avatarHeight);

        int greetingWidth = Math.min(300, Math.max(0, avatarX - 2 * EDGE_GAP));
        int greetingHeight = 58;
        int greetingX = Math.max(EDGE_GAP, avatarX - PANEL_GAP - greetingWidth);
        int greetingY = Math.max(EDGE_GAP, avatarY + (avatarHeight - greetingHeight) / 2);
        greeting.setBounds(greetingX, greetingY, greetingWidth, greetingHeight);

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

    private JPanel createGreeting() {
        JPanel bubble = new JPanel(new BorderLayout(8, 0));
        bubble.setBackground(SwingTheme.PANEL);
        bubble.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingTheme.LINE, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 6)));
        JLabel message = new JLabel("Hi, I'm George. Ask me anything! :3");
        message.setFont(SwingTheme.BODY);
        message.setForeground(SwingTheme.NAVY);
        bubble.add(message, BorderLayout.CENTER);
        JButton clear = new JButton("\u00d7");
        clear.setToolTipText("Dismiss George's greeting");
        clear.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        clear.setContentAreaFilled(false);
        clear.setFocusPainted(false);
        clear.setForeground(SwingTheme.MUTED);
        clear.addActionListener(event -> bubble.setVisible(false));
        JPanel close = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        close.setOpaque(false);
        close.add(clear);
        bubble.add(close, BorderLayout.EAST);
        return bubble;
    }

    /** Circular, transparent avatar button without a painted surround. */
    private static final class CircularAvatarButton extends JButton {
        private CircularAvatarButton() {
            super(GeorgeAvatar.icon(48, 48));
            setBorder(null);
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
            int diameter = Math.max(0, Math.min(getWidth(), getHeight()) - 16);
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            Shape oldClip = copy.getClip();
            copy.clip(new Ellipse2D.Double(x, y, diameter, diameter));
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
