package interface_adapter.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import entity.entities.Trip;
import entity.entities.TripDay;
import entity.valueobjects.TransportationMode;
import interface_adapter.viewmodels.ShareState;
import interface_adapter.viewmodels.ShareViewModel;
import use_case.usecases.ShareTripOutputData;

final class ShareTripPresenterTest {

    @Test
    void exposesCopyableSuccessAndNonCopyableFailure() {
        final ShareViewModel viewModel = new ShareViewModel(
                new ShareState("", "", false));
        final ShareTripPresenter presenter = new ShareTripPresenter(viewModel);
        final Trip trip = new Trip(
                "t1", "Toronto", LocalDate.of(2026, 8, 10),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);

        presenter.presentSuccess(new ShareTripOutputData("Toronto itinerary", trip));
        assertEquals("Toronto itinerary", viewModel.getState().getShareText());
        assertTrue(viewModel.getState().canCopy());
        assertTrue(viewModel.getState().canSaveImages());
        assertFalse(viewModel.getState().isError());

        presenter.presentFailure("Trip not found");
        assertEquals("Trip not found", viewModel.getState().getMessage());
        assertFalse(viewModel.getState().canCopy());
        assertTrue(viewModel.getState().isError());
    }

    @Test
    void storesDayImagesForMultiDayShare() {
        final ShareViewModel viewModel = new ShareViewModel(
                new ShareState("", "", false));
        final ShareTripPresenter presenter = new ShareTripPresenter(viewModel);
        final Trip trip = new Trip(
                "trip-md",
                "Toronto",
                TransportationMode.WALKING,
                Arrays.asList(
                        new TripDay(LocalDate.of(2026, 8, 10), LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new TripDay(LocalDate.of(2026, 8, 11), LocalTime.of(9, 0), LocalTime.of(18, 0))));

        presenter.presentSuccess(new ShareTripOutputData("summary", trip));

        assertEquals(2, viewModel.getState().getDayImages().size());
        assertTrue(viewModel.getState().canSaveImages());
        assertTrue(viewModel.getState().getMessage().contains("2 day-plan images"));
    }
}
