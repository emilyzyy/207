package closeai.adapters.views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
    /** Warning band: amber enough to read as a caution, quiet enough not to shout. */
    public static final Color WARNING = new Color(146, 94, 6);
    public static final Color WARNING_SOFT = new Color(255, 248, 230);
    /** Surface for generated travel rows, so they sit below activities without a border. */
    public static final Color TRAVEL_SURFACE = new Color(247, 249, 252);
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

    public static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(BODY);
        button.setForeground(NAVY);
        button.setBackground(PANEL);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        return button;
    }

    /**
     * A small status pill, e.g. {@code Locked} or {@code Moved}.
     *
     * <p>The text carries the meaning; the tint is decoration. That ordering matters — the
     * Preview previously wrote " [locked]" into an HTML label, which is readable but reads
     * as an afterthought rather than a state.</p>
     */
    public static JLabel badge(String text, Color ink, Color surface) {
        JLabel badge = new JLabel(text);
        badge.setFont(SMALL.deriveFont(Font.BOLD));
        badge.setForeground(ink);
        badge.setBackground(surface);
        badge.setOpaque(true);
        badge.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
        badge.getAccessibleContext().setAccessibleName(text);
        return badge;
    }

    /** A section heading with a rule running to the right of it. */
    public static JPanel sectionHeader(String title, String trailing, Color accent) {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(title);
        label.setFont(SMALL.deriveFont(Font.BOLD));
        label.setForeground(accent);
        header.add(label, BorderLayout.WEST);

        JPanel rule = new JPanel();
        rule.setBackground(LINE);
        rule.setPreferredSize(new Dimension(10, 1));
        JPanel ruleHolder = new JPanel(new BorderLayout());
        ruleHolder.setOpaque(false);
        ruleHolder.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));
        ruleHolder.add(rule, BorderLayout.CENTER);
        header.add(ruleHolder, BorderLayout.CENTER);

        if (trailing != null && !trailing.isEmpty()) {
            JLabel note = new JLabel(trailing);
            note.setFont(SMALL);
            note.setForeground(MUTED);
            header.add(note, BorderLayout.EAST);
        }
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                header.getPreferredSize().height));
        return header;
    }

    /**
     * One before/after figure, as a small card: a bold {@code 0 → 12} over a caption.
     * Replaces a run-on sentence of numbers with three things the eye can compare.
     */
    public static JPanel metricCard(String value, String caption) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BLUE_SOFT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(214, 229, 250)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel figure = new JLabel(value);
        figure.setFont(BODY.deriveFont(Font.BOLD));
        figure.setForeground(NAVY);
        figure.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(figure);

        JLabel label = new JLabel(caption);
        label.setFont(SMALL);
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(label);

        card.getAccessibleContext().setAccessibleName(caption + ": " + value);
        return card;
    }

    /** A caution strip, visually separate from both the figures and the reasoning. */
    public static JPanel warningBand(java.util.List<String> messages) {
        JPanel band = new JPanel();
        band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
        band.setBackground(WARNING_SOFT);
        band.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, WARNING),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        band.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String message : messages) {
            JLabel line = new JLabel("<html>&#9888; " + message
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    + "</html>");
            line.setFont(SMALL);
            line.setForeground(WARNING);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            band.add(line);
        }
        band.getAccessibleContext().setAccessibleName(
                messages.size() + " scheduling warning" + (messages.size() == 1 ? "" : "s"));
        return band;
    }

    public static JButton placeholderButton(String text) {
        JButton button = new JButton(text);
        button.setFont(SMALL);
        button.setEnabled(false);
        button.setToolTipText("Not wired for this milestone");
        return button;
    }
}
