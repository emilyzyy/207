package views;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The spinner animates only while it is on screen.
 *
 * <p>A timer left running behind a finished schedule is a repaint every 90ms forever and
 * keeps Swing's timer thread awake for nothing — the kind of leak that never shows up in a
 * screenshot.</p>
 */
class SpinnerTest {

    @Test
    void theAnimationRunsOnlyWhileTheSpinnerIsVisible() {
        Spinner spinner = new Spinner();
        assertTrue(spinner.isSpinning(), "a spinner starts visible, so it starts running");

        spinner.setVisible(false);
        assertFalse(spinner.isSpinning(), "hidden means stopped, not merely invisible");

        spinner.setVisible(true);
        assertTrue(spinner.isSpinning());

        spinner.removeNotify();
        assertFalse(spinner.isSpinning(),
                "leaving the component tree must stop the timer too");
    }

    @Test
    void theSpinnerStaysOutOfTheFocusOrder() {
        assertFalse(new Spinner().isFocusable(),
                "it is decorative; the status sentence beside it carries the meaning");
    }
}
