package views;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JToggleButton;

/**
 * A switch: the same component as a checkbox, wearing a pill and a knob.
 *
 * <p>Extends {@link JToggleButton} rather than painting a bespoke widget from scratch, so
 * every behaviour a checkbox already earned is kept for free — Space toggles it, it takes
 * focus, action listeners fire, {@code setEnabled(false)} works, and a screen reader sees an
 * ordinary two-state button. Only the painting is replaced: an on/off pill whose state is
 * shown by both colour and knob position, so it does not rely on colour alone.</p>
 *
 * <p>The label lives outside the control. A switch with text painted inside it is neither a
 * switch nor a button, and the settings dialog already lays labels beside its rows.</p>
 */
public final class ToggleSwitch extends JToggleButton {

    private static final int TRACK_WIDTH = 40;
    private static final int TRACK_HEIGHT = 22;
    private static final int KNOB_MARGIN = 3;

    private static final Color ON = SwingTheme.BLUE;
    private static final Color OFF = new Color(189, 197, 205);

    /**
     * A washed-out blue, not grey.
     *
     * <p>A switch that is fixed in the on position still has to look on. Painting it in the
     * plain disabled grey said "off, and you cannot change it" -- the exact opposite of
     * "this is always considered", which is what these rows exist to say.</p>
     */
    private static final Color ON_FIXED = new Color(158, 194, 236);

    private static final Color OFF_DISABLED = new Color(222, 226, 230);
    private static final Color KNOB = Color.WHITE;

    public ToggleSwitch(String accessibleName) {
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setPreferredSize(new Dimension(TRACK_WIDTH, TRACK_HEIGHT));
        setMinimumSize(new Dimension(TRACK_WIDTH, TRACK_HEIGHT));
        setMaximumSize(new Dimension(TRACK_WIDTH, TRACK_HEIGHT));
        getAccessibleContext().setAccessibleName(accessibleName);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int width = TRACK_WIDTH;
        int height = TRACK_HEIGHT;
        int x = (getWidth() - width) / 2;
        int y = (getHeight() - height) / 2;

        final Color track;
        if (isSelected()) {
            track = isEnabled() ? ON : ON_FIXED;
        } else {
            track = isEnabled() ? OFF : OFF_DISABLED;
        }
        g.setColor(track);
        g.fillRoundRect(x, y, width, height, height, height);

        int knobSize = height - 2 * KNOB_MARGIN;
        int knobX = isSelected()
                ? x + width - KNOB_MARGIN - knobSize
                : x + KNOB_MARGIN;
        g.setColor(KNOB);
        g.fillOval(knobX, y + KNOB_MARGIN, knobSize, knobSize);

        if (isFocusOwner()) {
            g.setColor(SwingTheme.NAVY);
            g.drawRoundRect(x - 2, y - 2, width + 4, height + 4, height + 4, height + 4);
        }
        g.dispose();
    }
}
