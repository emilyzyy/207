package closeai.adapters.controllers;

import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.application.usecases.ShareItineraryInputBoundary;
import closeai.application.usecases.ShareItineraryInputData;
import java.util.function.Supplier;

/** Swing controller for exporting the active itinerary as a shareable PNG. */
public final class ShareItineraryController {
    private final ShareItineraryInputBoundary interactor;
    private final Supplier<String> tripId;

    public ShareItineraryController(
            ShareItineraryInputBoundary interactor, DayPlanViewModel viewModel) {
        if (interactor == null || viewModel == null) {
            throw new IllegalArgumentException("Share controller dependencies are required");
        }
        this.interactor = interactor;
        this.tripId = () -> viewModel.getState().getTripId();
    }

    public ShareItineraryController(
            ShareItineraryInputBoundary interactor, Supplier<String> tripId) {
        if (interactor == null || tripId == null) {
            throw new IllegalArgumentException("Share controller dependencies are required");
        }
        this.interactor = interactor;
        this.tripId = tripId;
    }

    public void execute() {
        String currentTripId = tripId.get();
        if (currentTripId == null || currentTripId.trim().isEmpty()) {
            interactor.execute(new ShareItineraryInputData(""));
            return;
        }
        interactor.execute(new ShareItineraryInputData(currentTripId.trim()));
    }
}
