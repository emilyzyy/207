package use_case.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import entity.entities.Trip;
import entity.valueobjects.TransportationMode;
import use_case.ports.AccountService;
import use_case.ports.TripRepository;

final class CreateTripUseCaseTest {

    @Test
    void createsAndPersistsTrimmedTrip() {
        final RecordingTripRepository repository = new RecordingTripRepository();
        final RecordingCreateTripOutputBoundary output =
                new RecordingCreateTripOutputBoundary();
        final CreateTripUseCase interactor = new CreateTripUseCase(repository, output);

        interactor.execute(new CreateTripInputData(
                "  Montreal  ",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.TRANSIT));

        assertNotNull(output.succeeded.getTrip().getId());
        assertEquals("Montreal", output.succeeded.getTrip().getDestination());
        assertEquals(output.succeeded.getTrip(), repository.saved);
    }

    @Test
    void rejectsBlankDestinationAndDoesNotSave() {
        final RecordingTripRepository repository = new RecordingTripRepository();
        final RecordingCreateTripOutputBoundary output =
                new RecordingCreateTripOutputBoundary();
        final CreateTripUseCase interactor = new CreateTripUseCase(repository, output);

        interactor.execute(new CreateTripInputData(
                " ",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.WALKING));

        assertEquals("Destination is required", output.failedMessage);
        assertNull(repository.saved);
    }

    @Test
    void rejectsMissingRequiredValues() {
        final RecordingCreateTripOutputBoundary output =
                new RecordingCreateTripOutputBoundary();
        final CreateTripUseCase interactor =
                new CreateTripUseCase(new RecordingTripRepository(), output);

        interactor.execute(new CreateTripInputData(
                "Toronto", null, LocalTime.NOON, LocalTime.of(18, 0),
                TransportationMode.WALKING));
        assertEquals("Date is required", output.failedMessage);

        interactor.execute(new CreateTripInputData(
                "Toronto", LocalDate.now(), null, LocalTime.of(18, 0),
                TransportationMode.WALKING));
        assertEquals("Start time is required", output.failedMessage);

        interactor.execute(new CreateTripInputData(
                "Toronto", LocalDate.now(), LocalTime.NOON, null,
                TransportationMode.WALKING));
        assertEquals("End time is required", output.failedMessage);

        interactor.execute(new CreateTripInputData(
                "Toronto", LocalDate.now(), LocalTime.NOON,
                LocalTime.of(18, 0), null));
        assertEquals("Transportation mode is required", output.failedMessage);
    }

    @Test
    void rejectsEndThatDoesNotFollowStart() {
        final RecordingCreateTripOutputBoundary output =
                new RecordingCreateTripOutputBoundary();
        final CreateTripUseCase interactor =
                new CreateTripUseCase(new RecordingTripRepository(), output);

        interactor.execute(new CreateTripInputData(
                "Toronto",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(18, 0),
                LocalTime.of(9, 0),
                TransportationMode.DRIVING));

        assertEquals("Trip end must follow start", output.failedMessage);
    }

    @Test
    void createsMultiDayTripWithConsecutiveDates() {
        final RecordingTripRepository repository = new RecordingTripRepository();
        final RecordingCreateTripOutputBoundary output =
                new RecordingCreateTripOutputBoundary();
        final CreateTripUseCase interactor = new CreateTripUseCase(repository, output);

        interactor.execute(new CreateTripInputData(
                "Toronto",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.WALKING,
                3));

        final Trip result = output.succeeded.getTrip();
        assertEquals(3, result.getDayCount());
        assertEquals(LocalDate.of(2026, 8, 2), result.getDay(0).getDate());
        assertEquals(LocalDate.of(2026, 8, 3), result.getDay(1).getDate());
        assertEquals(LocalDate.of(2026, 8, 4), result.getDay(2).getDate());
    }

    @Test
    void rejectsZeroDayTrip() {
        final RecordingCreateTripOutputBoundary output =
                new RecordingCreateTripOutputBoundary();
        final CreateTripUseCase interactor =
                new CreateTripUseCase(new RecordingTripRepository(), output);

        interactor.execute(new CreateTripInputData(
                "Toronto",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.WALKING,
                0));

        assertEquals("Trip must last at least one day", output.failedMessage);
        assertNull(output.succeeded);
    }

    @Test
    void sharesWithCompanionsWhenAccountServiceIsAvailable() {
        final RecordingAccountService account = new RecordingAccountService();
        final RecordingTripRepository repository = new RecordingTripRepository();
        final RecordingCreateTripOutputBoundary output =
                new RecordingCreateTripOutputBoundary();
        final CreateTripUseCase interactor =
                new CreateTripUseCase(repository, output, account);

        interactor.execute(new CreateTripInputData(
                "Toronto",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.WALKING,
                1,
                java.util.List.of("friend-1", "friend-2")));

        assertNotNull(output.succeeded.getTrip().getId());
        assertEquals(output.succeeded.getTrip().getId(), account.sharedTripId);
        assertEquals(java.util.List.of("friend-1", "friend-2"),
                account.sharedMemberIds);
    }

    @Test
    void doesNotShareWhenNoCompanionsAreChosen() {
        final RecordingAccountService account = new RecordingAccountService();
        final RecordingCreateTripOutputBoundary output =
                new RecordingCreateTripOutputBoundary();
        final CreateTripUseCase interactor =
                new CreateTripUseCase(new RecordingTripRepository(), output, account);

        interactor.execute(new CreateTripInputData(
                "Toronto",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.WALKING));

        assertNotNull(output.succeeded.getTrip().getId());
        assertNull(account.sharedTripId);
    }

    @Test
    void createsSuccessfullyWithoutAnAccountService() {
        final RecordingCreateTripOutputBoundary output =
                new RecordingCreateTripOutputBoundary();
        final CreateTripUseCase interactor =
                new CreateTripUseCase(new RecordingTripRepository(), output);

        interactor.execute(new CreateTripInputData(
                "Toronto",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.WALKING,
                1,
                java.util.List.of("friend-1")));

        assertNotNull(output.succeeded.getTrip().getId());
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
            final Optional<Trip> result;
            if (saved != null && saved.getId().equals(id)) {
                result = Optional.of(saved);
            }
            else {
                result = Optional.empty();
            }
            return result;
        }

        @Override
        public java.util.List<Trip> findAll() {
            final java.util.List<Trip> result;
            if (saved == null) {
                result = java.util.List.of();
            }
            else {
                result = java.util.List.of(saved);
            }
            return result;
        }
    }

    private static final class RecordingCreateTripOutputBoundary
            implements CreateTripOutputBoundary {
        private CreateTripOutputData succeeded;
        private String failedMessage;

        @Override
        public void presentSuccess(CreateTripOutputData outputData) {
            succeeded = outputData;
        }

        @Override
        public void presentFailure(String errorMessage) {
            failedMessage = errorMessage;
        }
    }

    private static final class RecordingAccountService implements AccountService {
        private String sharedTripId;
        private java.util.List<String> sharedMemberIds;

        @Override
        public entity.entities.User ensureProfile(String preferredUsername) {
            return null;
        }

        @Override
        public Optional<entity.entities.User> currentProfile() {
            return Optional.empty();
        }

        @Override
        public entity.entities.User updateProfile(String username, String email,
                                                  String password, String avatarColor,
                                                  String avatarImage) {
            return null;
        }

        @Override
        public Optional<entity.entities.User> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public entity.entities.Friendship sendFriendRequest(String username) {
            return null;
        }

        @Override
        public void acceptFriendRequest(String friendshipId) {
        }

        @Override
        public void cancelFriendRequest(String friendshipId) {
        }

        @Override
        public void removeFriend(String friendshipId) {
        }

        @Override
        public java.util.List<entity.entities.Friendship> listIncomingRequests() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<entity.entities.Friendship> listOutgoingRequests() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<entity.entities.Friendship> listAcceptedFriendships() {
            return java.util.List.of();
        }

        @Override
        public void setTripMembers(String tripId,
                                   java.util.Map<String, entity.valueobjects.TripAccessRole>
                                           memberRoles) {
            sharedTripId = tripId;
            sharedMemberIds = new java.util.ArrayList<>(memberRoles.keySet());
        }

        @Override
        public java.util.List<String> listTripCompanionUsernames(String tripId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<entity.entities.TripParticipant> listTripParticipants(
                String tripId) {
            return java.util.List.of();
        }

        @Override
        public entity.valueobjects.TripAccessLevel getMyTripAccess(String tripId) {
            return entity.valueobjects.TripAccessLevel.VIEW;
        }

        @Override
        public Optional<entity.entities.User> getTripOwner(String tripId) {
            return Optional.empty();
        }
    }
}
