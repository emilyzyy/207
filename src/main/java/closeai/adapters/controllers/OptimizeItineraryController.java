package closeai.adapters.controllers;

import closeai.application.usecases.OptimizeItineraryInputBoundary;
import closeai.application.usecases.OptimizeItineraryInputData;

/** Swing controller for first-pass current-itinerary compaction. */
public final class OptimizeItineraryController {
    private final OptimizeItineraryInputBoundary interactor;
    private final String tripId;

    public OptimizeItineraryController(
            OptimizeItineraryInputBoundary interactor, String tripId) {
        if (interactor == null || tripId == null || tripId.trim().isEmpty()) {
            throw new IllegalArgumentException("Optimize controller dependencies are required");
        }
        this.interactor = interactor;
        this.tripId = tripId.trim();
    }

    public void execute() {
        interactor.execute(new OptimizeItineraryInputData(tripId));
    }
}
