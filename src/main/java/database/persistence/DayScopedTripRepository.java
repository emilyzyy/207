package database.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import entity.entities.Trip;
import use_case.ports.TripRepository;

/**
 * Repository decorator that exposes only one day of each stored trip.
 *
 * <p>Feeds single-day scheduling engines (Autoschedule) a per-day {@link Trip} projection
 * ({@link Trip#projectDay(int)}). Saving a projected trip writes that day's schedule back
 * into the real multi-day trip, so a failed day never mutates the aggregate. The active day
 * comes from a supplier so the UI can switch days without rebuilding this decorator.</p>
 */
public final class DayScopedTripRepository implements TripRepository {
    private final TripRepository delegate;
    private final Supplier<Integer> dayIndex;

    public DayScopedTripRepository(TripRepository delegate, Supplier<Integer> dayIndex) {
        if (delegate == null) {
            throw new IllegalArgumentException("Trip repository is required");
        }
        if (dayIndex == null) {
            throw new IllegalArgumentException("Day index supplier is required");
        }
        this.delegate = delegate;
        this.dayIndex = dayIndex;
    }

    @Override
    public Trip save(Trip trip) {
        final int index = dayIndex.get();
        final Trip real = delegate.findById(trip.getId())
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        real.replaceDaySchedule(index, trip.getScheduledEvents());
        return delegate.save(real);
    }

    @Override
    public Optional<Trip> findById(String id) {
        return delegate.findById(id).map(this::projectActiveDay);
    }

    @Override
    public List<Trip> findAll() {
        final List<Trip> projected = new ArrayList<Trip>();
        for (Trip trip : delegate.findAll()) {
            projected.add(projectActiveDay(trip));
        }
        return projected;
    }

    @Override
    public boolean deleteById(String id) {
        return delegate.deleteById(id);
    }

    private Trip projectActiveDay(Trip trip) {
        final int index = dayIndex.get();
        if (index < 0 || index >= trip.getDayCount()) {
            throw new IllegalArgumentException("Day index out of range");
        }
        return trip.projectDay(index);
    }
}
