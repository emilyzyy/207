package use_case.usecases;

import entity.entities.Trip;

/** Input boundary for creating and storing a new trip. */
public interface CreateTripInputBoundary {
    /**
     * Performs the e xe cu te operation.
     * @param inputData the i np ut da ta value
     * @return the result of the operation
     */
    Trip execute(CreateTripInputData inputData);
}
