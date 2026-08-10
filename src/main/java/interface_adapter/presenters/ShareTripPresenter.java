package interface_adapter.presenters;

import interface_adapter.viewmodels.ShareState;
import interface_adapter.viewmodels.ShareViewModel;
import use_case.usecases.ShareTripOutputBoundary;

/** Presents share results to an observable Swing state. */
public final class ShareTripPresenter implements ShareTripOutputBoundary {
    private final ShareViewModel viewModel;

    public ShareTripPresenter(ShareViewModel viewModel) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Share ViewModel is required");
        }
        this.viewModel = viewModel;
    }

    @Override
    public void presentSuccess(String shareText) {
        viewModel.setState(new ShareState(
                shareText,
                "Itinerary ready to copy and share.",
                false));
    }

    @Override
    public void presentFailure(String errorMessage) {
        viewModel.setState(new ShareState(
                "",
                errorMessage == null || errorMessage.trim().isEmpty()
                        ? "Unable to prepare this itinerary" : errorMessage,
                true));
    }
}
