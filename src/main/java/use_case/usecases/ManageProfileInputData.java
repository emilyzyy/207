package use_case.usecases;

/** Input for loading or updating the signed-in profile. */
public final class ManageProfileInputData {
    public enum Action {
        LOAD,
        UPDATE,
        SIGN_OUT
    }

    private final Action action;
    private final String username;
    private final String email;
    private final String avatarColor;
    private final String avatarImage;
    private final boolean changingPassword;
    private final String currentPassword;
    private final String newPassword;
    private final String confirmPassword;
    private final String sessionPassword;

    private ManageProfileInputData(
            Action action,
            String username,
            String email,
            String avatarColor,
            String avatarImage,
            boolean changingPassword,
            String currentPassword,
            String newPassword,
            String confirmPassword,
            String sessionPassword) {
        if (action == null) {
            throw new IllegalArgumentException("Profile action is required");
        }
        this.action = action;
        this.username = username == null ? "" : username.trim();
        this.email = email == null ? "" : email.trim();
        this.avatarColor = avatarColor;
        this.avatarImage = avatarImage;
        this.changingPassword = changingPassword;
        this.currentPassword = currentPassword == null ? "" : currentPassword;
        this.newPassword = newPassword == null ? "" : newPassword;
        this.confirmPassword = confirmPassword == null ? "" : confirmPassword;
        this.sessionPassword = sessionPassword == null ? "" : sessionPassword;
    }

    public static ManageProfileInputData load() {
        return new ManageProfileInputData(
                Action.LOAD, null, null, null, null, false, null, null, null, null);
    }

    public static ManageProfileInputData signOut() {
        return new ManageProfileInputData(
                Action.SIGN_OUT, null, null, null, null, false, null, null, null, null);
    }

    public static ManageProfileInputData update(
            String username,
            String email,
            String avatarColor,
            String avatarImage,
            boolean changingPassword,
            String currentPassword,
            String newPassword,
            String confirmPassword,
            String sessionPassword) {
        return new ManageProfileInputData(
                Action.UPDATE,
                username,
                email,
                avatarColor,
                avatarImage,
                changingPassword,
                currentPassword,
                newPassword,
                confirmPassword,
                sessionPassword);
    }

    public Action getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarColor() {
        return avatarColor;
    }

    public String getAvatarImage() {
        return avatarImage;
    }

    public boolean isChangingPassword() {
        return changingPassword;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public String getSessionPassword() {
        return sessionPassword;
    }
}
