package closeai.application.usecases;

import closeai.application.ports.ItineraryDataAccessInterface;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Updates destination, date, window, and transportation on an existing itinerary.
 * Rejects changes that would leave scheduled events outside the new trip window.
 */
public final class EditItineraryInteractor implements EditItineraryInputBoundary {
    private final ItineraryDataAccessInterface itineraryDataAccess;

    public EditItineraryInteractor(ItineraryDataAccessInterface itineraryDataAccess) {
        if (itineraryDataAccess == null) {
            throw new IllegalArgumentException("Itinerary data access is required");
        }
        this.itineraryDataAccess = itineraryDataAccess;
    }

    @Override
    public Trip execute(EditItineraryInputData inputData) {
        if (inputData == null) {
            throw new IllegalArgumentException("Edit itinerary input is required");
        }

        String itineraryId = requireText(inputData.getItineraryId(), "Itinerary id is required");
        String destination = requireText(inputData.getDestination(), "Destination is required");
        LocalDate date = requireNonNull(inputData.getDate(), "Date is required");
        LocalTime startTime = requireNonNull(inputData.getStartTime(), "Start time is required");
        LocalTime endTime = requireNonNull(inputData.getEndTime(), "End time is required");
        TransportationMode mode = requireNonNull(inputData.getTransportationMode(),
                "Transportation mode is required");

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Trip end must follow start");
        }

        Trip itinerary = itineraryDataAccess.loadItinerary(itineraryId)
                .orElseThrow(() -> new IllegalArgumentException("Itinerary not found"));

        ensureScheduleFitsWindow(itinerary, startTime, endTime);
        itinerary.updateOptions(destination.trim(), date, startTime, endTime, mode);
        return itineraryDataAccess.saveItinerary(itinerary);
    }

    private static void ensureScheduleFitsWindow(Trip itinerary, LocalTime startTime, LocalTime endTime) {
        for (ScheduledEvent event : itinerary.getScheduledEvents()) {
            if (event.getStartTime().isBefore(startTime) || event.getEndTime().isAfter(endTime)) {
                throw new IllegalArgumentException(
                        "Cannot change itinerary window: scheduled events would fall outside");
            }
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
