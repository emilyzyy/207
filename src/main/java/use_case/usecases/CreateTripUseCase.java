package use_case.usecases;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import entity.entities.Trip;
import entity.entities.TripDay;
import entity.valueobjects.TransportationMode;
import use_case.ports.AccountService;
import use_case.ports.TripRepository;

/**
 * Interactor that validates, creates, and persists a new trip aggregate.
 */
public final class CreateTripUseCase implements CreateTripInputBoundary {
    private final TripRepository trips;
    private final CreateTripOutputBoundary output;
    private final AccountService account;

    /**
     * Creates a create-trip interactor.
     *
     * @param trips the trip repository
     * @param output the output boundary for creation results
     * @throws IllegalArgumentException if any dependency is missing
     */
    public CreateTripUseCase(TripRepository trips, CreateTripOutputBoundary output) {
        this(trips, output, null);
    }

    /**
     * Creates a create-trip interactor that may also share the trip with companions.
     *
     * @param trips the trip repository
     * @param output the output boundary for creation results
     * @param account optional account service used to share with companions
     * @throws IllegalArgumentException if any required dependency is missing
     */
    public CreateTripUseCase(TripRepository trips, CreateTripOutputBoundary output,
                             AccountService account) {
        if (trips == null || output == null) {
            throw new IllegalArgumentException("Create trip dependencies are required");
        }
        this.trips = trips;
        this.output = output;
        this.account = account;
    }

    @Override
    public void execute(CreateTripInputData inputData) {
        try {
            final Trip saved = doExecute(inputData);
            output.presentSuccess(new CreateTripOutputData(
                    saved, "Trip created successfully"));
        }
        catch (IllegalArgumentException | IllegalStateException exception) {
            output.presentFailure(exception.getMessage());
        }
    }

    /**
     * Compatibility overload retained for the REST and legacy web entry points.
     *
     * @param destination destination name
     * @param date start date
     * @param start start time
     * @param end end time
     * @param mode transportation mode
     * @return the created trip
     */
    public Trip execute(
            String destination,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            TransportationMode mode) {
        return executeAndReturn(new CreateTripInputData(destination, date, start, end, mode));
    }

    /**
     * Compatibility overload retained for the REST and legacy web entry points.
     *
     * @param destination destination name
     * @param date start date
     * @param start start time
     * @param end end time
     * @param mode transportation mode
     * @param dayCount number of trip days
     * @return the created trip
     */
    public Trip execute(
            String destination,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            TransportationMode mode,
            int dayCount) {
        return executeAndReturn(new CreateTripInputData(
                destination, date, start, end, mode, dayCount));
    }

    /**
     * Executes creation synchronously for callers that need the created trip.
     *
     * @param inputData the validated trip details
     * @return the created trip
     */
    public Trip executeAndReturn(CreateTripInputData inputData) {
        return doExecute(inputData);
    }

    private Trip doExecute(CreateTripInputData inputData) {
        if (inputData == null) {
            throw new IllegalArgumentException("Create trip input is required");
        }

        final String destination = requireText(
                inputData.getDestination(), "Destination is required");
        final LocalDate date = requireNonNull(inputData.getDate(), "Date is required");
        final LocalTime start = requireNonNull(
                inputData.getStartTime(), "Start time is required");
        final LocalTime end = requireNonNull(
                inputData.getEndTime(), "End time is required");
        final TransportationMode mode = requireNonNull(
                inputData.getTransportationMode(), "Transportation mode is required");

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Trip end must follow start");
        }

        final int dayCount = inputData.getDayCount();
        if (dayCount < 1) {
            throw new IllegalArgumentException("Trip must last at least one day");
        }

        final List<TripDay> days = new ArrayList<TripDay>(dayCount);
        for (int i = 0; i < dayCount; i++) {
            days.add(new TripDay(date.plusDays(i), start, end));
        }

        final Trip trip = new Trip(
                UUID.randomUUID().toString(), destination, mode, days);
        final Trip saved = trips.save(trip);
        final List<String> companionIds = inputData.getCompanionIds();
        if (account != null && companionIds != null && !companionIds.isEmpty()) {
            account.setTripMembers(saved.getId(), companionIds);
        }
        return saved;
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
