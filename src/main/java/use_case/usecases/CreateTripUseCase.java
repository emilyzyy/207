package use_case.usecases;

import use_case.ports.TripRepository;
import entity.entities.Trip;
import entity.entities.TripDay;
import entity.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Interactor that validates, creates, and persists a new trip aggregate. */
public final class CreateTripUseCase implements CreateTripInputBoundary {
    private final TripRepository trips;

    public CreateTripUseCase(TripRepository trips) {
        if (trips == null) {
            throw new IllegalArgumentException("Trip repository is required");
        }
        this.trips = trips;
    }

    @Override
    public Trip execute(CreateTripInputData inputData) {
        if (inputData == null) {
            throw new IllegalArgumentException("Create trip input is required");
        }

        String destination = requireText(
                inputData.getDestination(), "Destination is required");
        LocalDate date = requireNonNull(inputData.getDate(), "Date is required");
        LocalTime start = requireNonNull(
                inputData.getStartTime(), "Start time is required");
        LocalTime end = requireNonNull(
                inputData.getEndTime(), "End time is required");
        TransportationMode mode = requireNonNull(
                inputData.getTransportationMode(), "Transportation mode is required");

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Trip end must follow start");
        }

        int dayCount = inputData.getDayCount();
        if (dayCount < 1) {
            throw new IllegalArgumentException("Trip must last at least one day");
        }

        List<TripDay> days = new ArrayList<TripDay>(dayCount);
        for (int i = 0; i < dayCount; i++) {
            days.add(new TripDay(date.plusDays(i), start, end));
        }

        Trip trip = new Trip(
                UUID.randomUUID().toString(), destination, mode, days);
        return trips.save(trip);
    }

    /**
     * Compatibility overload retained for the REST and legacy web entry points.
     */
    public Trip execute(
            String destination,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            TransportationMode mode) {
        return execute(new CreateTripInputData(destination, date, start, end, mode));
    }

    public Trip execute(
            String destination,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            TransportationMode mode,
            int dayCount) {
        return execute(new CreateTripInputData(
                destination, date, start, end, mode, dayCount));
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
