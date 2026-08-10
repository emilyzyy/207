package use_case.usecases;

import java.util.List;

import entity.entities.Trip;
import use_case.ports.TripRepository;

public final class ListTripsUseCase {
    private final TripRepository trips;

    public ListTripsUseCase(TripRepository trips) {
        this.trips = trips;
    }

    /**
     * Performs the e xe cu te operation.
     * @return the result of the operation
     */
    public List<Trip> execute() {
        return trips.findAll();
    }
}
