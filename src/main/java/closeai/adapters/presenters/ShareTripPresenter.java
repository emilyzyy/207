package closeai.adapters.presenters;

import closeai.adapters.viewmodels.ShareState;
import closeai.adapters.viewmodels.ShareViewModel;
import closeai.application.usecases.ShareTripOutputBoundary;

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
