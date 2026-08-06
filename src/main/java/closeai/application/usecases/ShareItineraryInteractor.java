package closeai.application.usecases;

import closeai.application.ports.ItineraryPngExporter;
import closeai.application.ports.TripRepository;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.EventType;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads an itinerary, builds a share card model, and exports PNG bytes through a port.
 */
public final class ShareItineraryInteractor implements ShareItineraryInputBoundary {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private final TripRepository trips;
    private final ItineraryPngExporter exporter;
    private final ShareItineraryOutputBoundary output;

    public ShareItineraryInteractor(
            TripRepository trips,
            ItineraryPngExporter exporter,
            ShareItineraryOutputBoundary output) {
        if (trips == null || exporter == null || output == null) {
            throw new IllegalArgumentException("Share itinerary dependencies are required");
        }
        this.trips = trips;
        this.exporter = exporter;
        this.output = output;
    }

    @Override
    public void execute(ShareItineraryInputData inputData) {
        try {
            String tripId = requireTripId(inputData);
            Trip trip = trips.findById(tripId)
                    .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
            if (trip.getScheduledEvents().isEmpty()) {
                throw new IllegalArgumentException(
                        "Add activities to the Day Plan before sharing");
            }

            ShareCardModel card = toCard(trip);
            byte[] png = exporter.export(card);
            if (png == null || png.length == 0) {
                throw new IllegalStateException("Share export produced an empty image");
            }
            output.presentSuccess(new ShareItineraryOutputData(png, suggestedFileName(trip)));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            output.presentFailure(exception.getMessage());
        } catch (RuntimeException exception) {
            output.presentFailure("Unable to create share image");
        }
    }

    private static String requireTripId(ShareItineraryInputData inputData) {
        if (inputData == null || inputData.getTripId() == null
                || inputData.getTripId().trim().isEmpty()) {
            throw new IllegalArgumentException("Trip id is required");
        }
        return inputData.getTripId().trim();
    }

    private static ShareCardModel toCard(Trip trip) {
        List<ShareCardModel.ShareCardLine> lines = new ArrayList<ShareCardModel.ShareCardLine>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            String range = event.getStartTime().format(TIME) + " – "
                    + event.getEndTime().format(TIME);
            boolean travel = event.getEventType() == EventType.TRAVEL;
            String title;
            if (travel) {
                title = event.getNotes() == null || event.getNotes().isEmpty()
                        ? "Travel" : event.getNotes();
            } else if (event.getActivity() != null) {
                title = event.getActivity().getName();
            } else {
                title = event.getNotes() == null || event.getNotes().isEmpty()
                        ? "Activity" : event.getNotes();
            }
            lines.add(new ShareCardModel.ShareCardLine(range, title, travel));
        }
        return new ShareCardModel(
                trip.getDestination(),
                trip.getDate(),
                trip.getTransportationMode().name(),
                lines);
    }

    private static String suggestedFileName(Trip trip) {
        String destination = trip.getDestination().trim().replaceAll("[^A-Za-z0-9]+", "-");
        if (destination.endsWith("-")) {
            destination = destination.substring(0, destination.length() - 1);
        }
        if (destination.isEmpty()) {
            destination = "Trip";
        }
        return "CloseAI-" + destination + "-" + trip.getDate() + ".png";
    }
}
