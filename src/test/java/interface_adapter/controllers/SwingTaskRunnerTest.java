package interface_adapter.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * The point of the background runner is that Autoschedule's waiting never happens on the
 * Swing thread, so the window stays responsive while routing and the search run.
 */
class SwingTaskRunnerTest {

    @Test
    void workRunsAwayFromTheEventThread() throws Exception {
        final AtomicBoolean ranOnEventThread = new AtomicBoolean(true);
        final CountDownLatch finished = new CountDownLatch(1);

        SwingUtilities.invokeAndWait(() -> {
            new SwingTaskRunner().run(() -> {
                ranOnEventThread.set(SwingUtilities.isEventDispatchThread());
                finished.countDown();
            });
        });

        assertTrue(finished.await(5, TimeUnit.SECONDS), "the work should have run");
        assertFalse(ranOnEventThread.get(),
                "slow work on the event thread would freeze the window mid-click");
    }

    @Test
    void theEventThreadIsFreedImmediatelyRatherThanWaiting() throws Exception {
        final CountDownLatch allowFinish = new CountDownLatch(1);
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicBoolean submitReturnedWhileWorking = new AtomicBoolean(false);

        SwingUtilities.invokeAndWait(() -> {
            new SwingTaskRunner().run(() -> {
                started.countDown();
                try {
                    allowFinish.await(5, TimeUnit.SECONDS);
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            // Reaching here while the work is still blocked is the whole point.
            submitReturnedWhileWorking.set(true);
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(submitReturnedWhileWorking.get(),
                "submitting work must not block the caller");
        allowFinish.countDown();
    }
}
