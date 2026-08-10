package use_case.usecases;

import use_case.ports.TripRepository;
import entity.entities.Trip;
import java.util.List;

public final class ListTripsUseCase {
    private final TripRepository trips;
    public ListTripsUseCase(TripRepository trips) { this.trips = trips; }
    public List<Trip> execute() {
        return trips.findAll();
    }
}
