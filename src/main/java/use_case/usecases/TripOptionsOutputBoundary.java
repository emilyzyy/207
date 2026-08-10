package use_case.usecases;

import entity.entities.Trip;

/** Presentation boundary for editing an existing trip's date and daily time window. */
public interface TripOptionsOutputBoundary {
    /**
     * Performs the p re se nt su cc es s operation.
     * @param message the m es sa ge value
     * @param trip the t ri p value
     */
    void presentSuccess(Trip trip, String message);

    /**
     * Performs the p re se nt fa il ur e operation.
     * @param message the m es sa ge value
     */
    void presentFailure(String message);
}
