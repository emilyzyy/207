package use_case.usecases;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import database.persistence.InMemoryTripRepository;
import entity.entities.Trip;
import entity.valueobjects.TransportationMode;
import use_case.ports.TripRepository;

final class DeleteTripUseCaseTest {
    @Test
    void deletesTheWholeTripAggregate() {
        final InMemoryTripRepository repository = new InMemoryTripRepository();
        final Trip trip = new Trip("trip-1", "Sicily", LocalDate.of(2026, 8, 10),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
        repository.save(trip);

        new DeleteTripUseCase(repository).execute(trip.getId());

        assertFalse(repository.findById(trip.getId()).isPresent());
    }

    @Test
    void rejectsAnUnknownTrip() {
        final DeleteTripUseCase useCase = new DeleteTripUseCase(new InMemoryTripRepository());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("missing"));
    }

    @Test
    void rejectsNullRepositoryBlankIdAndFailedDelete() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteTripUseCase(null));

        final DeleteTripUseCase useCase = new DeleteTripUseCase(new InMemoryTripRepository());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(" "));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(null));

        final Trip trip = new Trip("trip-1", "Sicily", LocalDate.of(2026, 8, 10),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
        final TripRepository sticky = new TripRepository() {
            @Override
            public Trip save(Trip value) {
                return value;
            }

            @Override
            public Optional<Trip> findById(String id) {
                return Optional.of(trip);
            }

            @Override
            public List<Trip> findAll() {
                return Collections.singletonList(trip);
            }

            @Override
            public boolean deleteById(String id) {
                return false;
            }
        };
        assertThrows(IllegalStateException.class,
                () -> new DeleteTripUseCase(sticky).execute("trip-1"));
    }
}
