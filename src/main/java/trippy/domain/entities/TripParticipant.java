package trippy.domain.entities;

import trippy.domain.valueobjects.TripAccessRole;

/** A person on a trip: either the owner or a shared member with a role. */
public final class TripParticipant {
    private final User user;
    private final TripAccessRole role;
    private final boolean owner;

    public TripParticipant(User user, TripAccessRole role, boolean owner) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }
        this.user = user;
        this.owner = owner;
        this.role = owner ? null : (role == null ? TripAccessRole.EDIT : role);
    }

    public static TripParticipant owner(User user) {
        return new TripParticipant(user, null, true);
    }

    public static TripParticipant member(User user, TripAccessRole role) {
        return new TripParticipant(user, role, false);
    }

    public User getUser() {
        return user;
    }

    public TripAccessRole getRole() {
        return role;
    }

    public boolean isOwner() {
        return owner;
    }
}
