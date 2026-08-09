package trippy.adapters.views;

/** Shared password checks for signup and profile edits. */
public final class PasswordRules {
    public static final int MIN_LENGTH = 6;

    private PasswordRules() {
    }

    /**
     * @return an error message, or {@code null} if the password is acceptable
     */
    public static String validateNewPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Please enter a password.";
        }
        if (password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters.";
        }
        return null;
    }

    /**
     * @return an error message, or {@code null} if both passwords match and the new one is valid
     */
    public static String validateNewPasswordPair(String password, String confirm) {
        String error = validateNewPassword(password);
        if (error != null) {
            return error;
        }
        if (confirm == null || !password.equals(confirm)) {
            return "Passwords do not match.";
        }
        return null;
    }
}
