package use_case.usecases;

import entity.entities.User;

/** Result of loading or updating the signed-in profile. */
public final class ManageProfileOutputData {
    private final User profile;
    private final String message;
    private final boolean error;
    private final boolean signedOut;
    private final boolean updated;

    public ManageProfileOutputData(User profile, String message, boolean error) {
        this(profile, message, error, false, false);
    }

    public ManageProfileOutputData(
            User profile, String message, boolean error, boolean signedOut, boolean updated) {
        this.profile = profile;
        this.message = message == null ? "" : message;
        this.error = error;
        this.signedOut = signedOut;
        this.updated = updated;
    }

    public static ManageProfileOutputData failure(String message) {
        return new ManageProfileOutputData(null, message, true, false, false);
    }

    public static ManageProfileOutputData signedOut() {
        return new ManageProfileOutputData(null, "Signed out.", false, true, false);
    }

    public static ManageProfileOutputData updated(User profile, String message) {
        return new ManageProfileOutputData(profile, message, false, false, true);
    }

    public User getProfile() {
        return profile;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return error;
    }

    public boolean isSignedOut() {
        return signedOut;
    }

    public boolean isUpdated() {
        return updated;
    }
}
