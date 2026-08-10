package interface_adapter.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import interface_adapter.viewmodels.ShareState;
import interface_adapter.viewmodels.ShareViewModel;

final class ShareTripPresenterTest {

    @Test
    void exposesCopyableSuccessAndNonCopyableFailure() {
        final ShareViewModel viewModel = new ShareViewModel(
                new ShareState("", "", false));
        final ShareTripPresenter presenter = new ShareTripPresenter(viewModel);

        presenter.presentSuccess("Toronto itinerary");
        assertEquals("Toronto itinerary", viewModel.getState().getShareText());
        assertTrue(viewModel.getState().canCopy());
        assertFalse(viewModel.getState().isError());

        presenter.presentFailure("Trip not found");
        assertEquals("Trip not found", viewModel.getState().getMessage());
        assertFalse(viewModel.getState().canCopy());
        assertTrue(viewModel.getState().isError());
    }
}
