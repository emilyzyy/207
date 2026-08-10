package interface_adapter.presenters;

import interface_adapter.viewmodels.ProfileState;
import interface_adapter.viewmodels.ProfileViewModel;
import use_case.usecases.ManageProfileOutputBoundary;
import use_case.usecases.ManageProfileOutputData;

/** Presents profile results to an observable Swing state. */
public final class ProfilePresenter implements ManageProfileOutputBoundary {
    private final ProfileViewModel viewModel;

    public ProfilePresenter(ProfileViewModel viewModel) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Profile ViewModel is required");
        }
        this.viewModel = viewModel;
    }

    @Override
    public void present(ManageProfileOutputData outputData) {
        viewModel.setState(new ProfileState(
                outputData.getProfile(),
                outputData.getMessage(),
                outputData.isError(),
                outputData.isUpdated(),
                outputData.isSignedOut()));
    }
}
