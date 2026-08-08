package closeai.application.ports;

import closeai.domain.entities.Friendship;
import closeai.domain.entities.User;
import java.util.List;
import java.util.Optional;

/** Profiles and friendships for signed-in Supabase accounts. */
public interface AccountService {
    /**
     * Ensures a profile row exists for the current session.
     * @param preferredUsername optional username from signup; blank → auto-assign
     */
    User ensureProfile(String preferredUsername);

    Optional<User> currentProfile();

    User updateProfile(String username, String email, String password,
                       String avatarColor, String avatarImage);

    Optional<User> findByUsername(String username);

    Friendship sendFriendRequest(String username);

    void acceptFriendRequest(String friendshipId);

    void cancelFriendRequest(String friendshipId);

    void removeFriend(String friendshipId);

    List<Friendship> listIncomingRequests();

    List<Friendship> listOutgoingRequests();

    List<User> listFriends();

    /** Replaces the shared editors on a trip (friends who can view and edit). Owner is unchanged. */
    void setTripMembers(String tripId, List<String> memberUserIds);

    /**
     * Usernames of people shared on the trip, excluding the current user.
     * Empty when the trip is not shared.
     */
    List<String> listTripCompanionUsernames(String tripId);
}
