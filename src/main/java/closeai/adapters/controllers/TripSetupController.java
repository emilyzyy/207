package closeai.adapters.controllers;

import closeai.application.usecases.CreateTripInputBoundary;
import closeai.application.usecases.CreateTripInputData;
import closeai.application.usecases.EditItineraryInputBoundary;
import closeai.application.usecases.EditItineraryInputData;
import closeai.application.usecases.TripSetupOutputBoundary;
import closeai.application.usecases.TripSetupOutputData;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

/** Swing controller for creating a trip and editing the active trip's options. */
public final class TripSetupController {
    private final CreateTripInputBoundary createTrip;
    private final EditItineraryInputBoundary editItinerary;
    private final Supplier<String> activeTripId;
    private final TripSetupOutputBoundary output;

    public TripSetupController(
            CreateTripInputBoundary createTrip,
            EditItineraryInputBoundary editItinerary,
            Supplier<String> activeTripId,
            TripSetupOutputBoundary output) {
        if (createTrip == null || editItinerary == null
                || activeTripId == null || output == null) {
            throw new IllegalArgumentException("Trip setup dependencies are required");
        }
        this.createTrip = createTrip;
        this.editItinerary = editItinerary;
        this.activeTripId = activeTripId;
        this.output = output;
    }

    public void execute(
            String destination,
            String date,
            String startTime,
            String endTime,
            String transportationMode) {
        try {
            LocalDate parsedDate = parseDate(date);
            LocalTime parsedStart = parseTime(startTime, "Start time");
            LocalTime parsedEnd = parseTime(endTime, "End time");
            TransportationMode parsedMode = parseMode(transportationMode);
            String tripId = activeTripId.get();

            Trip trip;
            boolean created = tripId == null || tripId.trim().isEmpty();
            if (created) {
                trip = createTrip.execute(new CreateTripInputData(
                        destination, parsedDate, parsedStart, parsedEnd, parsedMode));
            } else {
                trip = editItinerary.execute(new EditItineraryInputData(
                        tripId, destination, parsedDate, parsedStart, parsedEnd, parsedMode));
            }
            output.presentSuccess(new TripSetupOutputData(trip, created));
        } catch (IllegalArgumentException exception) {
            output.presentFailure(exception.getMessage());
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(requireText(value, "Date is required"));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Date must use YYYY-MM-DD");
        }
    }

    private static LocalTime parseTime(String value, String label) {
        try {
            return LocalTime.parse(requireText(value, label + " is required"));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(label + " must use HH:MM");
        }
    }

    private static TransportationMode parseMode(String value) {
        try {
            return TransportationMode.valueOf(
                    requireText(value, "Transportation mode is required"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Select a transportation mode");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
