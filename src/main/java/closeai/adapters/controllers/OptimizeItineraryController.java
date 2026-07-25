package closeai.adapters.controllers;

import closeai.application.usecases.OptimizeItineraryInputBoundary;
import closeai.application.usecases.OptimizeItineraryInputData;
import closeai.adapters.viewmodels.DayPlanViewModel;
import java.util.function.Supplier;

/** Swing controller for first-pass current-itinerary compaction. */
public final class OptimizeItineraryController {
    private final OptimizeItineraryInputBoundary interactor;
    private final Supplier<String> tripId;

    public OptimizeItineraryController(
            OptimizeItineraryInputBoundary interactor, String tripId) {
        if (interactor == null || tripId == null || tripId.trim().isEmpty()) {
            throw new IllegalArgumentException("Optimize controller dependencies are required");
        }
        this.interactor = interactor;
        this.tripId = () -> tripId.trim();
    }

    public OptimizeItineraryController(
            OptimizeItineraryInputBoundary interactor, DayPlanViewModel viewModel) {
        if (interactor == null || viewModel == null) {
            throw new IllegalArgumentException("Optimize controller dependencies are required");
        }
        this.interactor = interactor;
        this.tripId = () -> viewModel.getState().getTripId();
    }

    public void execute() {
        String currentTripId = tripId.get();
        if (currentTripId == null || currentTripId.trim().isEmpty()) {
            return;
        }
        interactor.execute(new OptimizeItineraryInputData(currentTripId));
    }
}
