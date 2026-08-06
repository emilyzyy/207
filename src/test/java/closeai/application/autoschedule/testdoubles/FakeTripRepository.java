package closeai.application.autoschedule.testdoubles;

import closeai.application.ports.TripRepository;
import closeai.domain.entities.Trip;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** In-memory trip store that records whether anything was saved. */
public final class FakeTripRepository implements TripRepository {

    private Trip trip;
    private int saveCount;

    public FakeTripRepository(Trip trip) {
        this.trip = trip;
    }

    @Override
    public Trip save(Trip updated) {
        saveCount++;
        this.trip = updated;
        return updated;
    }

    @Override
    public Optional<Trip> findById(String id) {
        return trip != null && trip.getId().equals(id) ? Optional.of(trip) : Optional.empty();
    }

    @Override
    public List<Trip> findAll() {
        List<Trip> all = new ArrayList<>();
        if (trip != null) {
            all.add(trip);
        }
        return all;
    }

    public int getSaveCount() {
        return saveCount;
    }

    public Trip current() {
        return trip;
    }
}
