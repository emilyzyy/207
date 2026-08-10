package entity.entities;

/** A friend request or accepted friendship between two profiles. */
public final class Friendship {
    public enum Status {
        PENDING,
        ACCEPTED
    }

    private final String id;
    private final String requesterId;
    private final String addresseeId;
    private final Status status;
    private final User otherUser;

    public Friendship(
            String id,
            String requesterId,
            String addresseeId,
            Status status,
            User otherUser) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Friendship id is required");
        }
        if (requesterId == null || addresseeId == null || status == null || otherUser == null) {
            throw new IllegalArgumentException("Friendship fields are required");
        }
        this.id = id.trim();
        this.requesterId = requesterId.trim();
        this.addresseeId = addresseeId.trim();
        this.status = status;
        this.otherUser = otherUser;
    }

    public String getId() {
        return id;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public String getAddresseeId() {
        return addresseeId;
    }

    public Status getStatus() {
        return status;
    }
    /**
     * The other person relative to the current user (for list UIs).
     * @return the result of the operation
     */

    public User getOtherUser() {
        return otherUser;
    }
}
