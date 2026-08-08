package trippy.application.usecases;

import trippy.domain.entities.Trip;

/** Input boundary for creating and storing a new trip. */
public interface CreateTripInputBoundary {
    Trip execute(CreateTripInputData inputData);
}
