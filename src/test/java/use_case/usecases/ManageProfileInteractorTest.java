package use_case.usecases;

import entity.entities.Friendship;
import entity.entities.TripParticipant;
import entity.entities.User;
import entity.valueobjects.TripAccessLevel;
import entity.valueobjects.TripAccessRole;
import use_case.ports.AccountService;
import use_case.ports.AuthService;
import use_case.ports.AuthSession;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManageProfileInteractorTest {

    @Test
    void updatesProfileAndValidatesPasswordChange() {
        FakeAccount account = new FakeAccount();
        account.profile = new User("me", "bianca", "b@example.com");
        FakeAuth auth = new FakeAuth();
        RecordingOutput output = new RecordingOutput();
        ManageProfileInteractor interactor = new ManageProfileInteractor(account, auth, output);

        interactor.execute(ManageProfileInputData.update(
                "bianca2", "b2@example.com", "#FFFFFF", null,
                true, "wrong", "newpass", "newpass", "secret"));
        assertTrue(output.last.isError());
        assertEquals("Current password is incorrect.", output.last.getMessage());

        interactor.execute(ManageProfileInputData.update(
                "bianca2", "b2@example.com", "#FFFFFF", null,
                true, "secret", "newpass", "newpass", "secret"));
        assertFalse(output.last.isError());
        assertTrue(output.last.isUpdated());
        assertEquals("bianca2", output.last.getProfile().getUsername());
        assertEquals("newpass", account.lastPassword);
    }

    @Test
    void signsOutThroughAuthPort() {
        FakeAccount account = new FakeAccount();
        account.profile = new User("me", "bianca", "b@example.com");
        FakeAuth auth = new FakeAuth();
        RecordingOutput output = new RecordingOutput();
        ManageProfileInteractor interactor = new ManageProfileInteractor(account, auth, output);

        interactor.execute(ManageProfileInputData.signOut());

        assertTrue(output.last.isSignedOut());
        assertTrue(auth.signedOut);
    }

    private static final class RecordingOutput implements ManageProfileOutputBoundary {
        private ManageProfileOutputData last;

        @Override
        public void present(ManageProfileOutputData outputData) {
            last = outputData;
        }
    }

    private static final class FakeAuth implements AuthService {
        private boolean signedOut;

        @Override
        public AuthSession signUp(String email, String password) {
            return null;
        }

        @Override
        public AuthSession signIn(String email, String password) {
            return null;
        }

        @Override
        public AuthSession updateCredentials(String email, String password) {
            return null;
        }

        @Override
        public void signOut() {
            signedOut = true;
        }

        @Override
        public Optional<AuthSession> currentSession() {
            return Optional.empty();
        }
    }

    private static final class FakeAccount implements AccountService {
        private User profile;
        private String lastPassword;

        @Override
        public User ensureProfile(String preferredUsername) {
            return profile;
        }

        @Override
        public Optional<User> currentProfile() {
            return Optional.ofNullable(profile);
        }

        @Override
        public User updateProfile(String username, String email, String password,
                                  String avatarColor, String avatarImage) {
            lastPassword = password;
            profile = new User(profile.getId(), username, email, avatarColor, avatarImage);
            return profile;
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public Friendship sendFriendRequest(String username) {
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
        public List<Friendship> listIncomingRequests() {
            return List.of();
        }

        @Override
        public List<Friendship> listOutgoingRequests() {
            return List.of();
        }

        @Override
        public List<Friendship> listAcceptedFriendships() {
            return List.of();
        }

        @Override
        public void setTripMembers(String tripId, Map<String, TripAccessRole> memberRoles) {
        }

        @Override
        public List<String> listTripCompanionUsernames(String tripId) {
            return List.of();
        }

        @Override
        public List<TripParticipant> listTripParticipants(String tripId) {
            return List.of();
        }

        @Override
        public TripAccessLevel getMyTripAccess(String tripId) {
            return TripAccessLevel.OWNER;
        }

        @Override
        public Optional<User> getTripOwner(String tripId) {
            return Optional.ofNullable(profile);
        }
    }
}
