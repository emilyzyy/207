package trippy.application.usecases;

import trippy.application.ports.TripRepository;
import trippy.domain.entities.Trip;
import java.util.List;

public final class ListTripsUseCase {
    private final TripRepository trips;
    public ListTripsUseCase(TripRepository trips) { this.trips = trips; }
    public List<Trip> execute() {
        return trips.findAll();
    }
}
