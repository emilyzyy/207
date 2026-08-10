package entity.valueobjects;

/** Access level for a shared itinerary member (not the owner). */
public enum TripAccessRole {
    VIEW,
    EDIT,
    ADMIN;

    /**
     * Performs the t od b operation.
     * @return the result of the operation
     */
    public String toDb() {
        return name().toLowerCase();
    }

    /**
     * Performs the d is pl ay na me operation.
     * @return the result of the operation
     */
    public String displayName() {
        switch (this) {
            case VIEW:
                return "View";
            case EDIT:
                return "Edit";
            case ADMIN:
                return "Admin";
            default:
                return name();
        }
    }

    /**
     * Performs the f ro md b operation.
     * @param value the v al ue value
     * @return the result of the operation
     */
    public static TripAccessRole fromDb(String value) {
        if (value == null || value.trim().isEmpty()) {
            return EDIT;
        }
        try {
            return TripAccessRole.valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException exception) {
            return EDIT;
        }
    }

    /**
     * Performs the c an ed it it in er ar y operation.
     * @return the result of the operation
     */
    public boolean canEditItinerary() {
        return this == EDIT || this == ADMIN;
    }

    /**
     * Performs the c an ma na ge pe op le operation.
     * @return the result of the operation
     */
    public boolean canManagePeople() {
        return this == ADMIN;
    }
}
