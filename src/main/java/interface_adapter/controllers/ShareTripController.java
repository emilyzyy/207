package interface_adapter.controllers;

import use_case.usecases.ShareTripInputBoundary;
import java.util.function.Supplier;

/** Converts the active Swing trip selection into a share-use-case request. */
public final class ShareTripController {
    private final ShareTripInputBoundary shareTrip;
    private final Supplier<String> activeTripId;

    public ShareTripController(
            ShareTripInputBoundary shareTrip,
            Supplier<String> activeTripId) {
        if (shareTrip == null || activeTripId == null) {
            throw new IllegalArgumentException("Share dependencies are required");
        }
        this.shareTrip = shareTrip;
        this.activeTripId = activeTripId;
    }

    public void execute() {
        shareTrip.execute(activeTripId.get());
    }
}
