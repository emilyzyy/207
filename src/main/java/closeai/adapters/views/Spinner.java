package closeai.adapters.views;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A small rotating arc, shown while the scheduler is searching.
 *
 * <p>Autoschedule can take a second or two on a full day, and a sentence alone leaves the
 * user unsure whether anything is happening or the app has simply stopped. A moving thing
 * answers that without claiming to know how long it will take — there is no honest
 * percentage to show, because the search finishes when it finishes.</p>
 *
 * <p>The timer only runs while the component is showing. A spinner that keeps ticking behind
 * a finished schedule is a repaint every 90ms forever, and it would also keep the Swing
 * timer thread awake for nothing.</p>
 */
public final class Spinner extends JComponent {

    private static final int SIZE = 14;
    private static final int STEP_DEGREES = 30;
    private static final int FRAME_MILLIS = 90;
    private static final float STROKE = 2f;

    private final Timer timer;
    private int angle;

    public Spinner() {
        setPreferredSize(new Dimension(SIZE, SIZE));
        setMinimumSize(new Dimension(SIZE, SIZE));
        setMaximumSize(new Dimension(SIZE, SIZE));
        setOpaque(false);
        // Decorative, and deliberately left out of the focus and accessibility trees: the
        // status sentence beside it carries the meaning, and a screen reader announcing a
        // spinning circle would only add noise to it.
        setFocusable(false);
        timer = new Timer(FRAME_MILLIS, event -> {
            angle = (angle + STEP_DEGREES) % 360;
            repaint();
        });
        // The invariant is simply "visible means spinning", and a fresh component is
        // visible. Starting here rather than waiting for a setVisible(true) that may never
        // come means a spinner added to a panel animates without any further ceremony.
        timer.start();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            angle = 0;
            timer.start();
        } else {
            timer.stop();
        }
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    /** True while the animation is running; lets tests assert it stops when hidden. */
    boolean isSpinning() {
        return timer.isRunning();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new java.awt.BasicStroke(STROKE, java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));

        int inset = (int) STROKE;
        int diameter = SIZE - 2 * inset;

        g.setColor(SwingTheme.LINE);
        g.draw(new Arc2D.Double(inset, inset, diameter, diameter, 0, 360, Arc2D.OPEN));

        g.setColor(SwingTheme.BLUE);
        g.draw(new Arc2D.Double(inset, inset, diameter, diameter, 90 - angle, -100,
                Arc2D.OPEN));
        g.dispose();
    }
}
