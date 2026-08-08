package trippy.application.usecases;

import trippy.domain.entities.Trip;
import trippy.domain.valueobjects.TransportationMode;
import trippy.infrastructure.persistence.InMemoryTripRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DeleteTripUseCaseTest {
    @Test
    void deletesTheWholeTripAggregate() {
        InMemoryTripRepository repository = new InMemoryTripRepository();
        Trip trip = new Trip("trip-1", "Sicily", LocalDate.of(2026, 8, 10),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
        repository.save(trip);

        new DeleteTripUseCase(repository).execute(trip.getId());

        assertFalse(repository.findById(trip.getId()).isPresent());
    }

    @Test
    void rejectsAnUnknownTrip() {
        DeleteTripUseCase useCase = new DeleteTripUseCase(new InMemoryTripRepository());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("missing"));
    }
}
