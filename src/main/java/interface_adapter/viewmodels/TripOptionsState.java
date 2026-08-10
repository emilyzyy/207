package interface_adapter.viewmodels;

import java.time.LocalDate;
import java.time.LocalTime;

import entity.entities.Trip;

/** Immutable create/edit trip setup state. */
public final class TripOptionsState {
    private final String tripId;
    private final String destination;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String message;
    private final boolean error;

    public TripOptionsState(
            String destination, LocalDate date, LocalTime startTime, LocalTime endTime) {
        this(null, destination, date, startTime, endTime, "", false);
    }

    public TripOptionsState(
            String tripId,
            String destination,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String message,
            boolean error) {
        this.tripId = tripId == null ? "" : tripId;
        this.destination = destination == null ? "" : destination;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.message = message == null ? "" : message;
        this.error = error;
    }

    /**
     * Performs the f ro mt ri p operation.
     * @param message the m es sa ge value
     * @param error the e rr or value
     * @param trip the t ri p value
     * @return the result of the operation
     */
    public static TripOptionsState fromTrip(Trip trip, String message, boolean error) {
        return new TripOptionsState(
                trip.getId(),
                trip.getDestination(),
                trip.getDate(),
                trip.getStartTime(),
                trip.getEndTime(),
                message,
                error);
    }

    /**
     * Performs the w it hf ee db ac k operation.
     * @param feedbackIsError the f ee db ac ki se rr or value
     * @param feedback the f ee db ac k value
     * @return the result of the operation
     */
    public TripOptionsState withFeedback(String feedback, boolean feedbackIsError) {
        return new TripOptionsState(
                tripId, destination, date, startTime, endTime, feedback, feedbackIsError);
    }

    public String getTripId() {
        return tripId;
    }

    /**
     * Performs the h as ac ti ve tr ip operation.
     * @return the result of the operation
     */
    public boolean hasActiveTrip() {
        return !tripId.trim().isEmpty();
    }

    public String getDestination() {
        return destination;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return error;
    }
}
