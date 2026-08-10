package use_case.usecases;

import use_case.ports.ItineraryDataAccessInterface;
import entity.entities.Trip;
import entity.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Updates destination, date, window, and transportation on an existing itinerary.
 * Scheduled events are retained where possible, clipped at a changed boundary, or removed when
 * both endpoints fall outside the new day window.
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

        itinerary.updateOptionsPreservingSchedule(
                destination.trim(), date, startTime, endTime, mode);
        return itineraryDataAccess.saveItinerary(itinerary);
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
