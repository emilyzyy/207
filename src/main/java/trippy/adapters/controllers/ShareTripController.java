package trippy.adapters.controllers;

import trippy.application.usecases.ShareTripInputBoundary;
import trippy.application.usecases.ShareTripOutputBoundary;
import java.util.function.Supplier;

/** Converts the active Swing trip selection into a share-use-case request. */
public final class ShareTripController {
    private final ShareTripInputBoundary shareTrip;
    private final Supplier<String> activeTripId;
    private final ShareTripOutputBoundary output;

    public ShareTripController(
            ShareTripInputBoundary shareTrip,
            Supplier<String> activeTripId,
            ShareTripOutputBoundary output) {
        if (shareTrip == null || activeTripId == null || output == null) {
            throw new IllegalArgumentException("Share dependencies are required");
        }
        this.shareTrip = shareTrip;
        this.activeTripId = activeTripId;
        this.output = output;
    }

    public void execute() {
        try {
            output.presentSuccess(shareTrip.execute(activeTripId.get()));
        } catch (IllegalArgumentException exception) {
            output.presentFailure(exception.getMessage());
        }
    }
}
