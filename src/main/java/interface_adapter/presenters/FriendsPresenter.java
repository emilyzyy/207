package interface_adapter.presenters;

import interface_adapter.viewmodels.FriendsState;
import interface_adapter.viewmodels.FriendsViewModel;
import use_case.usecases.ManageFriendsOutputBoundary;
import use_case.usecases.ManageFriendsOutputData;

/** Presents friends-hub results to an observable Swing state. */
public final class FriendsPresenter implements ManageFriendsOutputBoundary {
    private final FriendsViewModel viewModel;

    public FriendsPresenter(FriendsViewModel viewModel) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Friends ViewModel is required");
        }
        this.viewModel = viewModel;
    }

    @Override
    public void present(ManageFriendsOutputData outputData) {
        viewModel.setState(new FriendsState(
                outputData.getIncoming(),
                outputData.getOutgoing(),
                outputData.getAccepted(),
                outputData.getMessage(),
                outputData.isError()));
    }
}
