package trippy.domain.valueobjects;

/** What the signed-in user can do on a trip. */
public enum TripAccessLevel {
    OWNER,
    ADMIN,
    EDIT,
    VIEW,
    NONE;

    public boolean canEditItinerary() {
        return this == OWNER || this == ADMIN || this == EDIT;
    }

    public boolean canManagePeople() {
        return this == OWNER || this == ADMIN;
    }
}
