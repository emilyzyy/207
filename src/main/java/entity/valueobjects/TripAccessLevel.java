package entity.valueobjects;

/** What the signed-in user can do on a trip. */
public enum TripAccessLevel {
    OWNER,
    ADMIN,
    EDIT,
    VIEW,
    NONE;

    /**
     * Performs the c an ed it it in er ar y operation.
     * @return the result of the operation
     */
    public boolean canEditItinerary() {
        return this == OWNER || this == ADMIN || this == EDIT;
    }

    /**
     * Performs the c an ma na ge pe op le operation.
     * @return the result of the operation
     */
    public boolean canManagePeople() {
        return this == OWNER || this == ADMIN;
    }
}
