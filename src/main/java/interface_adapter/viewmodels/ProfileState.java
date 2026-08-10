package interface_adapter.viewmodels;

import entity.entities.User;

/** Immutable profile dialog state. */
public final class ProfileState {
    private final User profile;
    private final String message;
    private final boolean error;
    private final boolean saved;
    private final boolean signedOut;

    public ProfileState(
            User profile,
            String message,
            boolean error,
            boolean saved,
            boolean signedOut) {
        this.profile = profile;
        this.message = message == null ? "" : message;
        this.error = error;
        this.saved = saved;
        this.signedOut = signedOut;
    }

    public static ProfileState empty() {
        return new ProfileState(null, "", false, false, false);
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

    public boolean isSaved() {
        return saved;
    }

    public boolean isSignedOut() {
        return signedOut;
    }
}
