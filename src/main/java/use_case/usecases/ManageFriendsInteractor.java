package use_case.usecases;

import java.util.List;

import entity.entities.Friendship;
import entity.entities.User;
import use_case.ports.AccountService;

/**
 * Interactor for loading and mutating friendships.
 * Friendship rules live here; {@link AccountService} only persists.
 */
public final class ManageFriendsInteractor implements ManageFriendsInputBoundary {
    private final AccountService account;
    private final ManageFriendsOutputBoundary output;

    public ManageFriendsInteractor(AccountService account, ManageFriendsOutputBoundary output) {
        if (account == null || output == null) {
            throw new IllegalArgumentException("Friends dependencies are required");
        }
        this.account = account;
        this.output = output;
    }

    @Override
    public void execute(ManageFriendsInputData inputData) {
        try {
            if (inputData == null) {
                throw new IllegalArgumentException("Friends input is required");
            }
            String message = "";
            switch (inputData.getAction()) {
                case LOAD:
                    break;
                case SEND_REQUEST:
                    message = sendRequest(inputData.getUsername());
                    break;
                case ACCEPT:
                    message = accept(inputData.getFriendshipId());
                    break;
                case CANCEL:
                    message = cancel(inputData.getFriendshipId());
                    break;
                case REMOVE:
                    message = remove(inputData.getFriendshipId());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown friends action");
            }
            output.present(snapshot(message, false));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            try {
                output.present(snapshot(exception.getMessage(), true));
            } catch (RuntimeException ignored) {
                output.present(ManageFriendsOutputData.failure(exception.getMessage()));
            }
        } catch (RuntimeException exception) {
            output.present(ManageFriendsOutputData.failure(
                    exception.getMessage() == null
                            ? "Could not update friends."
                            : exception.getMessage()));
        }
    }

    private String sendRequest(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a username to send a request.");
        }
        String targetUsername = username.trim();
        User me = account.currentProfile()
                .orElseGet(() -> account.ensureProfile(null));
        User target = account.findByUsername(targetUsername).orElseThrow(
                () -> new IllegalStateException("No user found with that username."));
        if (target.getId().equals(me.getId())) {
            throw new IllegalStateException("You cannot friend yourself.");
        }
        if (isAcceptedFriend(target.getId())) {
            throw new IllegalStateException("You are already friends with this person");
        }
        if (hasPendingWith(target.getId())) {
            throw new IllegalStateException(
                    "You already have a request or friendship with that user.");
        }
        Friendship created = account.sendFriendRequest(targetUsername);
        return "Request sent to @" + created.getOtherUser().getUsername() + ".";
    }

    private String accept(String friendshipId) {
        Friendship request = requireFriendshipId(friendshipId, account.listIncomingRequests());
        account.acceptFriendRequest(friendshipId);
        return "You are now friends with @" + request.getOtherUser().getUsername() + ".";
    }

    private String cancel(String friendshipId) {
        Friendship request = requireFriendshipId(friendshipId, account.listOutgoingRequests());
        account.cancelFriendRequest(friendshipId);
        return "Cancelled request to @" + request.getOtherUser().getUsername() + ".";
    }

    private String remove(String friendshipId) {
        Friendship friendship = requireFriendshipId(friendshipId, account.listAcceptedFriendships());
        account.removeFriend(friendshipId);
        return "Removed @" + friendship.getOtherUser().getUsername()
                + ". You can send them a new request anytime.";
    }

    private boolean isAcceptedFriend(String otherUserId) {
        for (Friendship friendship : account.listAcceptedFriendships()) {
            if (otherUserId.equals(friendship.getOtherUser().getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPendingWith(String otherUserId) {
        return containsOther(account.listIncomingRequests(), otherUserId)
                || containsOther(account.listOutgoingRequests(), otherUserId);
    }

    private static boolean containsOther(List<Friendship> friendships, String otherUserId) {
        for (Friendship friendship : friendships) {
            if (otherUserId.equals(friendship.getOtherUser().getId())) {
                return true;
            }
        }
        return false;
    }

    private static Friendship requireFriendshipId(String friendshipId, List<Friendship> candidates) {
        if (friendshipId == null || friendshipId.trim().isEmpty()) {
            throw new IllegalArgumentException("Friendship id is required");
        }
        for (Friendship friendship : candidates) {
            if (friendshipId.equals(friendship.getId())) {
                return friendship;
            }
        }
        throw new IllegalStateException("That friendship request was not found.");
    }

    private ManageFriendsOutputData snapshot(String message, boolean error) {
        return new ManageFriendsOutputData(
                account.listIncomingRequests(),
                account.listOutgoingRequests(),
                account.listAcceptedFriendships(),
                message,
                error);
    }
}
