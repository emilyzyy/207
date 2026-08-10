package use_case.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import entity.entities.Friendship;
import entity.entities.TripParticipant;
import entity.entities.User;
import entity.valueobjects.TripAccessLevel;
import entity.valueobjects.TripAccessRole;
import use_case.ports.AccountService;

final class ManageFriendsInteractorTest {

    @Test
    void rejectsSelfFriendAndAlreadyFriends() {
        final FakeAccount account = new FakeAccount();
        account.me = new User("me", "bianca", "b@example.com");
        account.users.put("bianca", account.me);
        account.users.put("alex", new User("alex-id", "alex", "a@example.com"));
        final RecordingOutput output = new RecordingOutput();
        final ManageFriendsInteractor interactor = new ManageFriendsInteractor(account, output);

        interactor.execute(ManageFriendsInputData.sendRequest("bianca"));
        assertTrue(output.last.isError());
        assertEquals("You cannot friend yourself.", output.last.getMessage());

        account.accepted.add(new Friendship(
                "f1", "me", "alex-id", Friendship.Status.ACCEPTED, account.users.get("alex")));
        interactor.execute(ManageFriendsInputData.sendRequest("alex"));
        assertTrue(output.last.isError());
        assertEquals("You are already friends with this person", output.last.getMessage());
    }

    @Test
    void sendsRequestAndAcceptsIncoming() {
        final FakeAccount account = new FakeAccount();
        account.me = new User("me", "bianca", "b@example.com");
        account.users.put("bianca", account.me);
        account.users.put("alex", new User("alex-id", "alex", "a@example.com"));
        final RecordingOutput output = new RecordingOutput();
        final ManageFriendsInteractor interactor = new ManageFriendsInteractor(account, output);

        interactor.execute(ManageFriendsInputData.sendRequest("alex"));
        assertFalse(output.last.isError());
        assertTrue(output.last.getMessage().contains("Request sent"));
        assertEquals(1, output.last.getOutgoing().size());

        final Friendship pending = output.last.getOutgoing().get(0);
        // Flip perspective: make it incoming for accept path by swapping lists.
        account.outgoing.clear();
        account.incoming.add(pending);
        interactor.execute(ManageFriendsInputData.accept(pending.getId()));
        assertFalse(output.last.isError());
        assertTrue(output.last.getMessage().contains("You are now friends"));
        assertEquals(1, output.last.getAccepted().size());
    }

    private static final class RecordingOutput implements ManageFriendsOutputBoundary {
        private ManageFriendsOutputData last;

        @Override
        public void present(ManageFriendsOutputData outputData) {
            last = outputData;
        }
    }

    private static final class FakeAccount implements AccountService {
        private User me;
        private final Map<String, User> users = new HashMap<String, User>();
        private final List<Friendship> incoming = new ArrayList<Friendship>();
        private final List<Friendship> outgoing = new ArrayList<Friendship>();
        private final List<Friendship> accepted = new ArrayList<Friendship>();

        @Override
        public User ensureProfile(String preferredUsername) {
            return me;
        }

        @Override
        public Optional<User> currentProfile() {
            return Optional.ofNullable(me);
        }

        @Override
        public User updateProfile(String username, String email, String password,
                                  String avatarColor, String avatarImage) {
            return me;
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.ofNullable(users.get(username));
        }

        @Override
        public Friendship sendFriendRequest(String username) {
            final User target = users.get(username);
            final Friendship created = new Friendship(
                    UUID.randomUUID().toString(), me.getId(), target.getId(),
                    Friendship.Status.PENDING, target);
            outgoing.add(created);
            return created;
        }

        @Override
        public void acceptFriendRequest(String friendshipId) {
            Friendship found = null;
            for (Friendship friendship : incoming) {
                if (friendship.getId().equals(friendshipId)) {
                    found = friendship;
                    break;
                }
            }
            if (found != null) {
                incoming.remove(found);
                accepted.add(new Friendship(
                        found.getId(), found.getRequesterId(), found.getAddresseeId(),
                        Friendship.Status.ACCEPTED, found.getOtherUser()));
            }
        }

        @Override
        public void cancelFriendRequest(String friendshipId) {
            outgoing.removeIf(friendship -> friendship.getId().equals(friendshipId));
        }

        @Override
        public void removeFriend(String friendshipId) {
            accepted.removeIf(friendship -> friendship.getId().equals(friendshipId));
        }

        @Override
        public List<Friendship> listIncomingRequests() {
            return new ArrayList<Friendship>(incoming);
        }

        @Override
        public List<Friendship> listOutgoingRequests() {
            return new ArrayList<Friendship>(outgoing);
        }

        @Override
        public List<Friendship> listAcceptedFriendships() {
            return new ArrayList<Friendship>(accepted);
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
            return Optional.ofNullable(me);
        }
    }
}
