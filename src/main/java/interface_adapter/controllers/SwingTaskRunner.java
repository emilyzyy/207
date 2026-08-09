package interface_adapter.controllers;

import javax.swing.SwingWorker;

/**
 * Runs Autoschedule's slow work on a background thread using SwingWorker.
 *
 * <p>Views update themselves from the view model on the event thread, so nothing here
 * needs to marshal results back; this only has to keep the waiting off the UI thread.</p>
 */
public final class SwingTaskRunner implements TaskRunner {

    @Override
    public void run(Runnable work) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                work.run();
                return null;
            }
        }.execute();
    }
}
