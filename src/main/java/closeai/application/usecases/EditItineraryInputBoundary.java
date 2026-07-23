package closeai.application.usecases;

import closeai.domain.entities.Trip;

/**
 * Application entry point for editing an existing itinerary (DIP: callers depend on this, not the interactor).
 */
public interface EditItineraryInputBoundary {
    Trip execute(EditItineraryInputData inputData);
}
