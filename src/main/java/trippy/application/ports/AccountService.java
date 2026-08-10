package trippy.application.ports;

import trippy.domain.entities.Friendship;
import trippy.domain.entities.TripParticipant;
import trippy.domain.entities.User;
import trippy.domain.valueobjects.TripAccessLevel;
import trippy.domain.valueobjects.TripAccessRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Accepted friendships for the current user (includes friendship id for removal).
     * Prefer this when the UI needs to delete a friend; use {@link #listFriends()} for
     * profile pickers.
     */
    List<Friendship> listAcceptedFriendships();

    /** Profiles of accepted friends (no friendship ids). */
    default List<User> listFriends() {
        List<User> friends = new java.util.ArrayList<User>();
        for (Friendship friendship : listAcceptedFriendships()) {
            friends.add(friendship.getOtherUser());
        }
        return friends;
    }

    /**
     * Replaces shared members on a trip. Owner is never stored as a member and cannot be removed.
     * Keys are friend user ids; values are View / Edit / Admin.
     */
    void setTripMembers(String tripId, Map<String, TripAccessRole> memberRoles);

    /** Convenience: share with friends at Edit access. */
    default void setTripMembers(String tripId, List<String> memberUserIds) {
        Map<String, TripAccessRole> roles = new LinkedHashMap<>();
        if (memberUserIds != null) {
            for (String memberId : memberUserIds) {
                if (memberId == null || memberId.trim().isEmpty()) {
                    continue;
                }
                roles.put(memberId.trim(), TripAccessRole.EDIT);
            }
        }
        setTripMembers(tripId, roles);
    }

    /**
     * Usernames of people shared on the trip, excluding the current user.
     * Empty when the trip is not shared.
     */
    List<String> listTripCompanionUsernames(String tripId);

    /** Owner first (tagged), then shared members with roles. */
    List<TripParticipant> listTripParticipants(String tripId);

    /** Friends currently on the trip (profiles only). */
    default List<User> listTripMembers(String tripId) {
        List<User> members = new java.util.ArrayList<>();
        for (TripParticipant participant : listTripParticipants(tripId)) {
            if (!participant.isOwner()) {
                members.add(participant.getUser());
            }
        }
        return members;
    }

    /** What the signed-in user can do on this trip. */
    TripAccessLevel getMyTripAccess(String tripId);

    Optional<User> getTripOwner(String tripId);
}
