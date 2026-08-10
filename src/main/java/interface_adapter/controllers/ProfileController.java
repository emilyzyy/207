package interface_adapter.controllers;

import use_case.usecases.ManageProfileInputBoundary;
import use_case.usecases.ManageProfileInputData;

/** Converts profile UI actions into use-case requests. */
public final class ProfileController {
    private final ManageProfileInputBoundary manageProfile;

    public ProfileController(ManageProfileInputBoundary manageProfile) {
        if (manageProfile == null) {
            throw new IllegalArgumentException("Manage profile use case is required");
        }
        this.manageProfile = manageProfile;
    }

    public void load() {
        manageProfile.execute(ManageProfileInputData.load());
    }

    public void save(
            String username,
            String email,
            String avatarColor,
            String avatarImage,
            boolean changingPassword,
            String currentPassword,
            String newPassword,
            String confirmPassword,
            String sessionPassword) {
        manageProfile.execute(ManageProfileInputData.update(
                username,
                email,
                avatarColor,
                avatarImage,
                changingPassword,
                currentPassword,
                newPassword,
                confirmPassword,
                sessionPassword));
    }

    public void signOut() {
        manageProfile.execute(ManageProfileInputData.signOut());
    }
}
