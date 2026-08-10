package interface_adapter.presenters;

import interface_adapter.viewmodels.ShareState;
import interface_adapter.viewmodels.ShareViewModel;
import java.awt.image.BufferedImage;
import java.util.Arrays;
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

    @Test
    void storesDayImagesForMultiDayShare() {
        ShareViewModel viewModel = new ShareViewModel(
                new ShareState("", "", false));
        ShareTripPresenter presenter = new ShareTripPresenter(viewModel);
        BufferedImage day1 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        BufferedImage day2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);

        presenter.presentSuccess("summary", Arrays.asList(day1, day2));

        assertEquals(2, viewModel.getState().getDayImages().size());
        assertTrue(viewModel.getState().canSaveImages());
        assertTrue(viewModel.getState().getMessage().contains("2 day-plan images"));
    }
}
