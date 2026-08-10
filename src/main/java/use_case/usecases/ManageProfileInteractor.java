package use_case.usecases;

import entity.entities.User;
import entity.valueobjects.PasswordPolicy;
import use_case.ports.AccountService;
import use_case.ports.AuthService;

/**
 * Interactor for loading and updating the signed-in profile.
 * Password policy and current-password checks live here, not in the View.
 */
public final class ManageProfileInteractor implements ManageProfileInputBoundary {
    private final AccountService account;
    private final AuthService auth;
    private final ManageProfileOutputBoundary output;

    public ManageProfileInteractor(
            AccountService account,
            AuthService auth,
            ManageProfileOutputBoundary output) {
        if (account == null || output == null) {
            throw new IllegalArgumentException("Profile dependencies are required");
        }
        this.account = account;
        this.auth = auth;
        this.output = output;
    }

    @Override
    public void execute(ManageProfileInputData inputData) {
        try {
            if (inputData == null) {
                throw new IllegalArgumentException("Profile input is required");
            }
            switch (inputData.getAction()) {
                case LOAD:
                    output.present(new ManageProfileOutputData(
                            account.ensureProfile(null), "", false));
                    break;
                case UPDATE:
                    output.present(ManageProfileOutputData.updated(
                            update(inputData), "Profile saved."));
                    break;
                case SIGN_OUT:
                    if (auth != null) {
                        auth.signOut();
                    }
                    output.present(ManageProfileOutputData.signedOut());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown profile action");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            output.present(ManageProfileOutputData.failure(exception.getMessage()));
        } catch (RuntimeException exception) {
            output.present(ManageProfileOutputData.failure(
                    exception.getMessage() == null
                            ? "Could not update profile."
                            : exception.getMessage()));
        }
    }

    private User update(ManageProfileInputData input) {
        if (input.getUsername().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        String passwordToSave = "";
        if (input.isChangingPassword()) {
            if (input.getCurrentPassword().isEmpty()
                    && input.getNewPassword().isEmpty()
                    && input.getConfirmPassword().isEmpty()) {
                throw new IllegalArgumentException(
                        "Enter your old and new passwords, or cancel password change.");
            }
            if (!input.getCurrentPassword().equals(input.getSessionPassword())) {
                throw new IllegalArgumentException("Current password is incorrect.");
            }
            final String passwordError = PasswordPolicy.validateNewPasswordPair(
                    input.getNewPassword(), input.getConfirmPassword());
            if (passwordError != null) {
                throw new IllegalArgumentException(passwordError);
            }
            passwordToSave = input.getNewPassword();
        }
        return account.updateProfile(
                input.getUsername(),
                input.getEmail(),
                passwordToSave,
                input.getAvatarColor(),
                input.getAvatarImage());
    }
}
