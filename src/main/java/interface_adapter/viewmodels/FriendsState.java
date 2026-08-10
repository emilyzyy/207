package interface_adapter.viewmodels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import entity.entities.Friendship;

/** Immutable friends-hub state for Swing. */
public final class FriendsState {
    private final List<Friendship> incoming;
    private final List<Friendship> outgoing;
    private final List<Friendship> accepted;
    private final String message;
    private final boolean error;

    public FriendsState(
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

    public static FriendsState empty() {
        return new FriendsState(
                Collections.<Friendship>emptyList(),
                Collections.<Friendship>emptyList(),
                Collections.<Friendship>emptyList(),
                "",
                false);
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

    private static List<Friendship> copy(List<Friendship> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Friendship>(source));
    }
}
