package use_case.usecases;

import entity.entities.Trip;
import use_case.ports.TripRepository;

/**
 * Interactor that builds share text and hands the trip to the output boundary.
 * Day-plan PNG rendering stays in interface adapters (no AWT here).
 */
public final class ShareTripUseCase implements ShareTripInputBoundary {
    private final GetTripSummaryUseCase summaries;
    private final TripRepository trips;
    private final ShareTripOutputBoundary output;

    public ShareTripUseCase(
            GetTripSummaryUseCase summaries,
            TripRepository trips,
            ShareTripOutputBoundary output) {
        if (summaries == null || trips == null || output == null) {
            throw new IllegalArgumentException("Share trip dependencies are required");
        }
        this.summaries = summaries;
        this.trips = trips;
        this.output = output;
    }

    @Override
    public void execute(String tripId) {
        try {
            if (tripId == null || tripId.trim().isEmpty()) {
                throw new IllegalArgumentException("Create a trip before sharing");
            }
            final String id = tripId.trim();
            final Trip trip = trips.findById(id).orElseThrow(
                    () -> new IllegalArgumentException("Trip not found"));
            final String shareText = summaries.execute(id);
            output.presentSuccess(new ShareTripOutputData(shareText, trip));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            output.presentFailure(exception.getMessage());
        }
    }

    /** Compatibility for REST callers that only need the text summary. */
    public String executeAndReturn(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            throw new IllegalArgumentException("Create a trip before sharing");
        }
        return summaries.execute(tripId.trim());
    }
}
