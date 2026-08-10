package use_case.usecases;

/** Input for one friends-hub action. */
public final class ManageFriendsInputData {
    public enum Action {
        LOAD,
        SEND_REQUEST,
        ACCEPT,
        CANCEL,
        REMOVE
    }

    private final Action action;
    private final String username;
    private final String friendshipId;

    private ManageFriendsInputData(Action action, String username, String friendshipId) {
        if (action == null) {
            throw new IllegalArgumentException("Friends action is required");
        }
        this.action = action;
        this.username = username == null ? "" : username.trim();
        this.friendshipId = friendshipId == null ? "" : friendshipId.trim();
    }

    public static ManageFriendsInputData load() {
        return new ManageFriendsInputData(Action.LOAD, null, null);
    }

    public static ManageFriendsInputData sendRequest(String username) {
        return new ManageFriendsInputData(Action.SEND_REQUEST, username, null);
    }

    public static ManageFriendsInputData accept(String friendshipId) {
        return new ManageFriendsInputData(Action.ACCEPT, null, friendshipId);
    }

    public static ManageFriendsInputData cancel(String friendshipId) {
        return new ManageFriendsInputData(Action.CANCEL, null, friendshipId);
    }

    public static ManageFriendsInputData remove(String friendshipId) {
        return new ManageFriendsInputData(Action.REMOVE, null, friendshipId);
    }

    public Action getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public String getFriendshipId() {
        return friendshipId;
    }
}
