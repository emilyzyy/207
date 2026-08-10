package interface_adapter.presenters;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

import interface_adapter.DayPlanShareImageRenderer;
import interface_adapter.viewmodels.ShareState;
import interface_adapter.viewmodels.ShareViewModel;
import use_case.usecases.ShareTripOutputBoundary;
import use_case.usecases.ShareTripOutputData;

/** Presents share results to an observable Swing state; renders day-plan PNGs here. */
public final class ShareTripPresenter implements ShareTripOutputBoundary {
    private final ShareViewModel viewModel;

    public ShareTripPresenter(ShareViewModel viewModel) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Share ViewModel is required");
        }
        this.viewModel = viewModel;
    }

    @Override
    public void presentSuccess(ShareTripOutputData outputData) {
        List<BufferedImage> dayImages = DayPlanShareImageRenderer.renderTrip(outputData.getTrip());
        int days = dayImages.size();
        String ready = days <= 1
                ? "Day plan image ready — scroll, save, or copy the text."
                : days + " day-plan images ready — scroll to see each day, then save or share.";
        viewModel.setState(new ShareState(
                outputData.getShareText(), ready, false, dayImages));
    }

    @Override
    public void presentFailure(String errorMessage) {
        viewModel.setState(new ShareState(
                "",
                errorMessage == null || errorMessage.trim().isEmpty()
                        ? "Unable to prepare this itinerary" : errorMessage,
                true,
                Collections.<BufferedImage>emptyList()));
    }
}
