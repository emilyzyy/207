package closeai.application.usecases;

import closeai.domain.entities.Trip;

/** Input boundary for creating and storing a new trip. */
public interface CreateTripInputBoundary {
    Trip execute(CreateTripInputData inputData);
}
