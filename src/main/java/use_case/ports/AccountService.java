package use_case.ports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import entity.entities.Friendship;
import entity.entities.TripParticipant;
import entity.entities.User;
import entity.valueobjects.TripAccessLevel;
import entity.valueobjects.TripAccessRole;

/** Profiles and friendships for signed-in Supabase accounts. */
public interface AccountService {
    /**
     * Ensures a profile row exists for the current session.
     * @param preferredUsername optional username from signup; blank → auto-assign
      * @return the result of the operation
     */
    User ensureProfile(String preferredUsername);

    /**
     * Performs the c ur re nt pr of il e operation.
     * @return the result of the operation
     */
    Optional<User> currentProfile();

    /**
     * Performs the u pd at ep ro fi le operation.
     * @param email the e ma il value
     * @param password the p as sw or d value
     * @param username the u se rn am e value
     * @return the result of the operation
     */
    User updateProfile(String username, String email, String password,
                       String avatarColor, String avatarImage);

    /**
     * Performs the f in db yu se rn am e operation.
     * @param username the u se rn am e value
     * @return the result of the operation
     */
    Optional<User> findByUsername(String username);

    /**
     * Performs the s en df ri en dr eq ue st operation.
     * @param username the u se rn am e value
     * @return the result of the operation
     */
    Friendship sendFriendRequest(String username);

    /**
     * Performs the a cc ep tf ri en dr eq ue st operation.
     * @param friendshipId the f ri en ds hi pi d value
     */
    void acceptFriendRequest(String friendshipId);

    /**
     * Performs the c an ce lf ri en dr eq ue st operation.
     * @param friendshipId the f ri en ds hi pi d value
     */
    void cancelFriendRequest(String friendshipId);

    /**
     * Performs the r em ov ef ri en d operation.
     * @param friendshipId the f ri en ds hi pi d value
     */
    void removeFriend(String friendshipId);

    /**
     * Performs the l is ti nc om in gr eq ue st s operation.
     * @return the result of the operation
     */
    List<Friendship> listIncomingRequests();

    /**
     * Performs the l is to ut go in gr eq ue st s operation.
     * @return the result of the operation
     */
    List<Friendship> listOutgoingRequests();

    /**
     * Performs the l is tf ri en ds operation.
     * @return the result of the operation
     */
    List<User> listFriends();

    /**
     * Replaces shared members on a trip. Owner is never stored as a member and cannot be removed.
     * Keys are friend user ids; values are View / Edit / Admin.
      * @param tripId the t ri pi d value
      * @param memberRoles the m em be rr ol es value
     */
    void setTripMembers(String tripId, Map<String, TripAccessRole> memberRoles);

    /**
     * Convenience: share with friends at Edit access.
     * @param memberUserIds the m em be ru se ri ds value
     * @param tripId the t ri pi d value
     */
    default void setTripMembers(String tripId, List<String> memberUserIds) {
        final Map<String, TripAccessRole> roles = new LinkedHashMap<>();
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
      * @param tripId the t ri pi d value
      * @return the result of the operation
     */
    List<String> listTripCompanionUsernames(String tripId);

    /**
     * Owner first (tagged), then shared members with roles.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    List<TripParticipant> listTripParticipants(String tripId);

    /**
     * Friends currently on the trip (profiles only).
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    default List<User> listTripMembers(String tripId) {
        final List<User> members = new java.util.ArrayList<>();
        for (TripParticipant participant : listTripParticipants(tripId)) {
            if (!participant.isOwner()) {
                members.add(participant.getUser());
            }
        }
        return members;
    }

    /**
     * What the signed-in user can do on this trip.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    TripAccessLevel getMyTripAccess(String tripId);

    /**
     * Performs the g et tr ip ow ne r operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    Optional<User> getTripOwner(String tripId);
}
