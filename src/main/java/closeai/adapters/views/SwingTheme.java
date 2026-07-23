package closeai.adapters.views;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.border.Border;

/** Shared visual constants derived from the retained web prototype. */
public final class SwingTheme {
    public static final Color NAVY = new Color(13, 35, 64);
    public static final Color BLUE = new Color(31, 104, 225);
    public static final Color BLUE_SOFT = new Color(238, 245, 255);
    public static final Color BACKGROUND = new Color(244, 247, 250);
    public static final Color PANEL = Color.WHITE;
    public static final Color LINE = new Color(216, 224, 232);
    public static final Color MUTED = new Color(91, 106, 123);
    public static final Color SUCCESS = new Color(26, 127, 83);
    public static final Color ERROR = new Color(181, 56, 48);
    public static final Font TITLE = new Font("SansSerif", Font.BOLD, 24);
    public static final Font HEADING = new Font("SansSerif", Font.BOLD, 17);
    public static final Font BODY = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font SMALL = new Font("SansSerif", Font.PLAIN, 11);

    private SwingTheme() {
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(12, 14, 12, 14));
    }

    public static void styleCard(JComponent component) {
        component.setBackground(PANEL);
        component.setBorder(cardBorder());
    }

    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(BODY.deriveFont(Font.BOLD));
        button.setForeground(Color.WHITE);
        button.setBackground(BLUE);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        return button;
    }

    public static JButton placeholderButton(String text) {
        JButton button = new JButton(text);
        button.setFont(SMALL);
        button.setEnabled(false);
        button.setToolTipText("Not wired for this milestone");
        return button;
    }
}
