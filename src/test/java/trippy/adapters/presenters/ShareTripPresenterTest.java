package trippy.adapters.presenters;

import trippy.adapters.viewmodels.ShareState;
import trippy.adapters.viewmodels.ShareViewModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShareTripPresenterTest {

    @Test
    void exposesCopyableSuccessAndNonCopyableFailure() {
        ShareViewModel viewModel = new ShareViewModel(
                new ShareState("", "", false));
        ShareTripPresenter presenter = new ShareTripPresenter(viewModel);

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
