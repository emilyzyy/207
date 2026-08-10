package views;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JToggleButton;

/** Compact sun/moon switch for the global Swing color theme. */
public final class ThemeToggleButton extends JToggleButton {
    public ThemeToggleButton() {
        setSelected(SwingTheme.isDarkMode());
        setPreferredSize(new Dimension(64, 34));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setToolTipText(isSelected() ? "Switch to light mode" : "Switch to dark mode");
        getAccessibleContext().setAccessibleName("Dark mode");
        addActionListener(event -> {
            SwingTheme.setDarkMode(isSelected());
            setToolTipText(isSelected() ? "Switch to light mode" : "Switch to dark mode");
            repaint();
        });
    }

    @Override protected void paintComponent(Graphics graphics) {
        final Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        final int h = getHeight() - 4;
        final int y = 2;
        g.setColor(isSelected() ? new Color(45, 49, 55) : new Color(232, 236, 241));
        g.fillRoundRect(1, y, getWidth() - 2, h, h, h);
        g.setColor(SwingTheme.LINE);
        g.drawRoundRect(1, y, getWidth() - 3, h - 1, h, h);
        final int diameter = h - 6;
        final int knobX = isSelected() ? getWidth() - diameter - 4 : 4;
        g.setColor(isSelected() ? Color.BLACK : Color.WHITE);
        g.fillOval(knobX, y + 3, diameter, diameter);
        final int cx = knobX + diameter / 2;
        final int cy = y + 3 + diameter / 2;
        if (isSelected()) {
            drawMoon(g, cx, cy, diameter / 3);

        } else {

            drawSun(g, cx, cy, diameter / 4);
        }
        g.dispose();
    }

    private void drawSun(Graphics2D g, int cx, int cy, int radius) {
        g.setColor(new Color(62, 66, 72));
        g.setStroke(new BasicStroke(1.8f));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        for (int index = 0; index < 8; index++) {
            final double angle = index * Math.PI / 4;
            final int x1 = cx + (int) Math.round(Math.cos(angle) * (radius + 3));
            final int y1 = cy + (int) Math.round(Math.sin(angle) * (radius + 3));
            final int x2 = cx + (int) Math.round(Math.cos(angle) * (radius + 6));
            final int y2 = cy + (int) Math.round(Math.sin(angle) * (radius + 6));
            g.drawLine(x1, y1, x2, y2);
        }
    }

    private void drawMoon(Graphics2D g, int cx, int cy, int radius) {
        g.setColor(Color.WHITE);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setColor(Color.BLACK);
        g.fillOval(cx - radius / 3, cy - radius, radius * 2, radius * 2);
    }
}
