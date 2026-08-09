package entity.valueobjects;

/** Access level for a shared itinerary member (not the owner). */
public enum TripAccessRole {
    VIEW,
    EDIT,
    ADMIN;

    public String toDb() {
        return name().toLowerCase();
    }

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

    public static TripAccessRole fromDb(String value) {
        if (value == null || value.trim().isEmpty()) {
            return EDIT;
        }
        try {
            return TripAccessRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return EDIT;
        }
    }

    public boolean canEditItinerary() {
        return this == EDIT || this == ADMIN;
    }

    public boolean canManagePeople() {
        return this == ADMIN;
    }
}
