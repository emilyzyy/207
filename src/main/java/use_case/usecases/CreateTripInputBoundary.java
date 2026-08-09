package use_case.usecases;

import entity.entities.Trip;

/** Input boundary for creating and storing a new trip. */
public interface CreateTripInputBoundary {
    Trip execute(CreateTripInputData inputData);
}
