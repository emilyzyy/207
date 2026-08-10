package views;

import entity.valueobjects.PasswordPolicy;

/** UI facade over {@link PasswordPolicy} for signup forms. */
public final class PasswordRules {
    public static final int MIN_LENGTH = PasswordPolicy.MIN_LENGTH;

    private PasswordRules() {
    }

    /**
     * @return an error message, or {@code null} if the password is acceptable
     */
    public static String validateNewPassword(String password) {
        return PasswordPolicy.validateNewPassword(password);
    }

    /**
     * @return an error message, or {@code null} if both passwords match and the new one is valid
     */
    public static String validateNewPasswordPair(String password, String confirm) {
        return PasswordPolicy.validateNewPasswordPair(password, confirm);
    }
}
