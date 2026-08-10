package use_case.usecases;

import entity.entities.Trip;

/**
 * Application entry point for editing an existing itinerary (DIP: callers depend on this, not the interactor).
 */
public interface EditItineraryInputBoundary {
    /**
     * Performs the e xe cu te operation.
     * @param inputData the i np ut da ta value
     * @return the result of the operation
     */
    Trip execute(EditItineraryInputData inputData);
}
