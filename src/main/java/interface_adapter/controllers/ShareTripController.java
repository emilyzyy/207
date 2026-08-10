package interface_adapter.controllers;

import interface_adapter.DayPlanShareImageRenderer;
import use_case.ports.TripRepository;
import use_case.usecases.ShareTripInputBoundary;
import use_case.usecases.ShareTripOutputBoundary;
import entity.entities.Trip;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Converts the active Swing trip selection into a share-use-case request. */
public final class ShareTripController {
    private final ShareTripInputBoundary shareTrip;
    private final Supplier<String> activeTripId;
    private final ShareTripOutputBoundary output;
    private final TripRepository trips;

    public ShareTripController(
            ShareTripInputBoundary shareTrip,
            Supplier<String> activeTripId,
            ShareTripOutputBoundary output) {
        this(shareTrip, activeTripId, output, null);
    }

    public ShareTripController(
            ShareTripInputBoundary shareTrip,
            Supplier<String> activeTripId,
            ShareTripOutputBoundary output,
            TripRepository trips) {
        if (shareTrip == null || activeTripId == null || output == null) {
            throw new IllegalArgumentException("Share dependencies are required");
        }
        this.shareTrip = shareTrip;
        this.activeTripId = activeTripId;
        this.output = output;
        this.trips = trips;
    }

    public void execute() {
        try {
            String tripId = activeTripId.get();
            String shareText = shareTrip.execute(tripId);
            List<BufferedImage> images = Collections.emptyList();
            if (trips != null) {
                Optional<Trip> trip = trips.findById(tripId);
                if (trip.isPresent()) {
                    images = DayPlanShareImageRenderer.renderTrip(trip.get());
                }
            }
            output.presentSuccess(shareText, images);
        } catch (IllegalArgumentException exception) {
            output.presentFailure(exception.getMessage());
        }
    }
}
