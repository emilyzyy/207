package use_case.usecases;

import entity.entities.Trip;

/** Presentation boundary for editing an existing trip's date and daily time window. */
public interface TripOptionsOutputBoundary {
    void presentSuccess(Trip trip, String message);
    void presentFailure(String message);
}
