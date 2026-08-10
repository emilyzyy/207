package use_case.usecases;

import entity.entities.Friendship;
import entity.entities.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Snapshot of friends hub data after a manage-friends action. */
public final class ManageFriendsOutputData {
    private final List<Friendship> incoming;
    private final List<Friendship> outgoing;
    private final List<Friendship> accepted;
    private final String message;
    private final boolean error;

    public ManageFriendsOutputData(
            List<Friendship> incoming,
            List<Friendship> outgoing,
            List<Friendship> accepted,
            String message,
            boolean error) {
        this.incoming = copy(incoming);
        this.outgoing = copy(outgoing);
        this.accepted = copy(accepted);
        this.message = message == null ? "" : message;
        this.error = error;
    }

    public static ManageFriendsOutputData failure(String message) {
        return new ManageFriendsOutputData(
                Collections.<Friendship>emptyList(),
                Collections.<Friendship>emptyList(),
                Collections.<Friendship>emptyList(),
                message,
                true);
    }

    public List<Friendship> getIncoming() {
        return incoming;
    }

    public List<Friendship> getOutgoing() {
        return outgoing;
    }

    public List<Friendship> getAccepted() {
        return accepted;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return error;
    }

    public List<User> getFriendProfiles() {
        List<User> profiles = new ArrayList<User>();
        for (Friendship friendship : accepted) {
            profiles.add(friendship.getOtherUser());
        }
        return Collections.unmodifiableList(profiles);
    }

    private static List<Friendship> copy(List<Friendship> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Friendship>(source));
    }
}
