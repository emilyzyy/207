package interface_adapter.controllers;

/**
 * Where slow work runs.
 *
 * <p>Autoschedule talks to the routing service, the forecast and the search before it can
 * answer, which is far too long to hold the Swing event thread: the window would freeze
 * mid-click. Injecting the runner keeps that decision out of the Controller, so the real
 * application hands it a background worker while tests hand it one that runs immediately
 * and stays predictable.</p>
 */
@FunctionalInterface
public interface TaskRunner {

    /**
     * Performs the r un operation.
     * @param work the w or k value
     */
    void run(Runnable work);

    /**
     * Runs work on the calling thread. Intended for tests.
     * @return the result of the operation
     */
    static TaskRunner immediate() {
        return Runnable::run;
    }
}
