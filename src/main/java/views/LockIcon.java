package views;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/**
 * A padlock, drawn rather than loaded.
 *
 * <p>The project has no icon pipeline: nothing in this package implements {@code Icon},
 * there is no {@code src/main/resources}, and pulling in an icon library for one padlock
 * would be a dependency out of all proportion to the need. Java2D draws it at whatever size
 * is asked for, stays crisp on a HiDPI display where a small bitmap would not, and takes its
 * colours from {@link SwingTheme} so it matches the rest of Trippy.</p>
 *
 * <p>The shackle is open on the unlocked state and closed on the locked one, so the two are
 * told apart by shape. Colour differs as well, but only as a second signal — a traveller who
 * cannot distinguish the blue from the grey still sees an open or closed loop.</p>
 */
final class LockIcon implements Icon {

    private final boolean locked;
    private final int size;

    LockIcon(boolean locked, int size) {
        this.locked = locked;
        this.size = size;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        final Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);

            final Color ink = locked ? SwingTheme.BLUE : SwingTheme.MUTED;
            final double unit = size / 16.0;

            // Body: a rounded rectangle across the lower half.
            final double bodyWidth = 11 * unit;
            final double bodyHeight = 8 * unit;
            final double bodyX = x + (size - bodyWidth) / 2.0;
            final double bodyY = y + size - bodyHeight - unit;
            final RoundRectangle2D body = new RoundRectangle2D.Double(
                    bodyX, bodyY, bodyWidth, bodyHeight, 2.5 * unit, 2.5 * unit);

            if (locked) {
                g.setColor(ink);
                g.fill(body);
            }
            else {
                g.setColor(SwingTheme.PANEL);
                g.fill(body);
                g.setColor(ink);
                g.setStroke(new BasicStroke((float) (1.4 * unit)));
                g.draw(body);
            }

            // Shackle: a closed arc when locked, swung open and to the right when not.
            final double shackleWidth = 7 * unit;
            final double shackleX = x + (size - shackleWidth) / 2.0;
            final double shackleY = y + 1.5 * unit;
            final double shackleHeight = 7 * unit;
            g.setColor(ink);
            g.setStroke(new BasicStroke((float) (1.6 * unit), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            if (locked) {
                g.draw(new Arc2D.Double(shackleX, shackleY, shackleWidth, shackleHeight,
                        0, 180, Arc2D.OPEN));
                // Straight legs down into the body, closing the loop.
                g.drawLine((int) Math.round(shackleX),
                        (int) Math.round(shackleY + shackleHeight / 2.0),
                        (int) Math.round(shackleX), (int) Math.round(bodyY));
                g.drawLine((int) Math.round(shackleX + shackleWidth),
                        (int) Math.round(shackleY + shackleHeight / 2.0),
                        (int) Math.round(shackleX + shackleWidth), (int) Math.round(bodyY));
            }
            else {
                // Open: the arc is lifted and only the left leg comes down, so the loop reads
                // as unfastened at a glance and at small sizes.
                g.draw(new Arc2D.Double(shackleX + 2 * unit, shackleY - unit,
                        shackleWidth, shackleHeight, 0, 200, Arc2D.OPEN));
                g.drawLine((int) Math.round(shackleX + 2 * unit),
                        (int) Math.round(shackleY - unit + shackleHeight / 2.0),
                        (int) Math.round(shackleX + 2 * unit), (int) Math.round(bodyY));
            }
        }
        finally {
            g.dispose();
        }
    }
}
