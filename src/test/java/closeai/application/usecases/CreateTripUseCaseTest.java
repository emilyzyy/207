package closeai.application.usecases;

import closeai.application.ports.TripRepository;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CreateTripUseCaseTest {

    @Test
    void createsAndPersistsTrimmedTrip() {
        RecordingTripRepository repository = new RecordingTripRepository();
        CreateTripUseCase interactor = new CreateTripUseCase(repository);

        Trip result = interactor.execute(new CreateTripInputData(
                "  Montreal  ",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.TRANSIT));

        assertNotNull(result.getId());
        assertEquals("Montreal", result.getDestination());
        assertEquals(result, repository.saved);
    }

    @Test
    void rejectsBlankDestinationAndDoesNotSave() {
        RecordingTripRepository repository = new RecordingTripRepository();
        CreateTripUseCase interactor = new CreateTripUseCase(repository);

        assertThrows(IllegalArgumentException.class, () -> interactor.execute(
                new CreateTripInputData(
                        " ",
                        LocalDate.of(2026, 8, 2),
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        TransportationMode.WALKING)));
        assertNull(repository.saved);
    }

    @Test
    void rejectsMissingRequiredValues() {
        CreateTripUseCase interactor =
                new CreateTripUseCase(new RecordingTripRepository());

        assertThrows(IllegalArgumentException.class, () -> interactor.execute(
                new CreateTripInputData(
                        "Toronto", null, LocalTime.NOON, LocalTime.of(18, 0),
                        TransportationMode.WALKING)));
        assertThrows(IllegalArgumentException.class, () -> interactor.execute(
                new CreateTripInputData(
                        "Toronto", LocalDate.now(), null, LocalTime.of(18, 0),
                        TransportationMode.WALKING)));
        assertThrows(IllegalArgumentException.class, () -> interactor.execute(
                new CreateTripInputData(
                        "Toronto", LocalDate.now(), LocalTime.NOON, null,
                        TransportationMode.WALKING)));
        assertThrows(IllegalArgumentException.class, () -> interactor.execute(
                new CreateTripInputData(
                        "Toronto", LocalDate.now(), LocalTime.NOON,
                        LocalTime.of(18, 0), null)));
    }

    @Test
    void rejectsEndThatDoesNotFollowStart() {
        CreateTripUseCase interactor =
                new CreateTripUseCase(new RecordingTripRepository());

        assertThrows(IllegalArgumentException.class, () -> interactor.execute(
                new CreateTripInputData(
                        "Toronto",
                        LocalDate.of(2026, 8, 2),
                        LocalTime.of(18, 0),
                        LocalTime.of(9, 0),
                        TransportationMode.DRIVING)));
    }

    @Test
    void createsMultiDayTripWithConsecutiveDates() {
        RecordingTripRepository repository = new RecordingTripRepository();
        CreateTripUseCase interactor = new CreateTripUseCase(repository);

        Trip result = interactor.execute(new CreateTripInputData(
                "Toronto",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.WALKING,
                3));

        assertEquals(3, result.getDayCount());
        assertEquals(LocalDate.of(2026, 8, 2), result.getDay(0).getDate());
        assertEquals(LocalDate.of(2026, 8, 3), result.getDay(1).getDate());
        assertEquals(LocalDate.of(2026, 8, 4), result.getDay(2).getDate());
    }

    @Test
    void rejectsZeroDayTrip() {
        CreateTripUseCase interactor =
                new CreateTripUseCase(new RecordingTripRepository());

        assertThrows(IllegalArgumentException.class, () -> interactor.execute(
                new CreateTripInputData(
                        "Toronto",
                        LocalDate.of(2026, 8, 2),
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        TransportationMode.WALKING,
                        0)));
    }

    private static final class RecordingTripRepository implements TripRepository {
        private Trip saved;

        @Override
        public Trip save(Trip trip) {
            saved = trip;
            return trip;
        }

        @Override
        public Optional<Trip> findById(String id) {
            return saved != null && saved.getId().equals(id)
                    ? Optional.of(saved) : Optional.empty();
        }

        @Override
        public java.util.List<Trip> findAll() {
            return saved == null ? java.util.List.of() : java.util.List.of(saved);
        }
    }
}
