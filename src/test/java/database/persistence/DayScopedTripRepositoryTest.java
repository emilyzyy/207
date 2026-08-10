package database.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.TripDay;
import entity.valueobjects.EventType;
import entity.valueobjects.TransportationMode;
import use_case.ports.TripRepository;

final class DayScopedTripRepositoryTest {

    @Test
    void findByIdProjectsTheActiveDay() {
        final AtomicInteger day = new AtomicInteger(1);
        final DayScopedTripRepository repository =
                new DayScopedTripRepository(new RecordingRepository(), day::get);

        final Optional<Trip> projected = repository.findById("trip-1");

        assertTrue(projected.isPresent());
        assertEquals(1, projected.get().getDayCount());
        assertEquals(LocalDate.of(2026, 8, 3), projected.get().getDate());
    }

    @Test
    void saveWritesScheduleBackIntoTheRealTripDay() {
        final RecordingRepository delegate = new RecordingRepository();
        final AtomicInteger day = new AtomicInteger(0);
        final DayScopedTripRepository repository = new DayScopedTripRepository(delegate, day::get);

        final ScheduledEvent travel = new ScheduledEvent(
                "event-travel", null,
                LocalTime.of(9, 0), LocalTime.of(9, 30), EventType.TRAVEL,
                "Travel");
        final Trip projected = repository.findById("trip-1").orElseThrow();
        final Trip saved = repository.save(projected.copyWithSchedule(List.of(travel)));

        assertEquals(2, saved.getDayCount());
        assertEquals(1, saved.getDay(0).getScheduledEvents().size());
        assertEquals("event-travel", saved.getDay(0).getScheduledEvents().get(0).getId());
        assertTrue(saved.getDay(1).getScheduledEvents().isEmpty());
        assertEquals(1, delegate.stored.getDay(0).getScheduledEvents().size());
    }

    @Test
    void saveWritesToTheSelectedDay() {
        final RecordingRepository delegate = new RecordingRepository();
        final AtomicInteger day = new AtomicInteger(1);
        final DayScopedTripRepository repository = new DayScopedTripRepository(delegate, day::get);

        final ScheduledEvent travel = new ScheduledEvent(
                "event-day2", null,
                LocalTime.of(10, 0), LocalTime.of(10, 30), EventType.TRAVEL,
                "Travel");
        final Trip projected = repository.findById("trip-1").orElseThrow();
        repository.save(projected.copyWithSchedule(List.of(travel)));

        assertTrue(delegate.stored.getDay(0).getScheduledEvents().isEmpty());
        assertEquals(1, delegate.stored.getDay(1).getScheduledEvents().size());
        assertEquals("event-day2", delegate.stored.getDay(1).getScheduledEvents().get(0).getId());
    }

    private static final class RecordingRepository implements TripRepository {
        private Trip stored;

        RecordingRepository() {
            stored = new Trip("trip-1", "Toronto", TransportationMode.WALKING, Arrays.asList(
                    new TripDay(LocalDate.of(2026, 8, 2),
                            LocalTime.of(9, 0), LocalTime.of(18, 0)),
                    new TripDay(LocalDate.of(2026, 8, 3),
                            LocalTime.of(9, 0), LocalTime.of(18, 0))));
        }

        @Override
        public Trip save(Trip trip) {
            stored = trip;
            return trip;
        }

        @Override
        public Optional<Trip> findById(String id) {
            return stored != null && stored.getId().equals(id)
                    ? Optional.of(stored) : Optional.empty();
        }

        @Override
        public List<Trip> findAll() {
            return stored == null ? List.of() : List.of(stored);
        }
    }
}
