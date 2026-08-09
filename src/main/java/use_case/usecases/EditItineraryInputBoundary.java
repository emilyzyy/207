package use_case.usecases;

import entity.entities.Trip;

/**
 * Application entry point for editing an existing itinerary (DIP: callers depend on this, not the interactor).
 */
public interface EditItineraryInputBoundary {
    Trip execute(EditItineraryInputData inputData);
}
