package views;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/** Button that can show a small red count badge in the top-right corner. */
public final class BadgedButton extends JButton {
    private static final Color BADGE = new Color(220, 53, 69);
    private int badgeCount;

    public BadgedButton(String text) {
        super(text);
        setFont(SwingTheme.BODY);
        setForeground(SwingTheme.NAVY);
        setBackground(SwingTheme.PANEL);
        setFocusPainted(false);
        setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(SwingTheme.LINE),
                javax.swing.BorderFactory.createEmptyBorder(7, 12, 7, 12)));
    }

    public void setBadgeCount(int count) {
        this.badgeCount = Math.max(0, count);
        repaint();
    }

    public int getBadgeCount() {
        return badgeCount;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (badgeCount <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        String label = badgeCount > 99 ? "99+" : Integer.toString(badgeCount);
        Font badgeFont = SwingTheme.SMALL.deriveFont(Font.BOLD, 10f);
        g2.setFont(badgeFont);
        FontMetrics metrics = g2.getFontMetrics();
        int textWidth = metrics.stringWidth(label);
        int diameter = Math.max(16, textWidth + 8);
        int x = getWidth() - diameter - 1;
        int y = 1;
        g2.setColor(BADGE);
        g2.fillOval(x, y, diameter, diameter);
        g2.setColor(Color.WHITE);
        int textX = x + (diameter - textWidth) / 2;
        int textY = y + (diameter - metrics.getHeight()) / 2 + metrics.getAscent();
        g2.drawString(label, textX, textY);
        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(Math.max(size.width, 72), size.height);
    }
}
