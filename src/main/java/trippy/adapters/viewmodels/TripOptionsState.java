package trippy.adapters.viewmodels;

import trippy.domain.entities.Trip;
import java.time.LocalDate;
import java.time.LocalTime;

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

    public TripOptionsState withFeedback(String feedback, boolean feedbackIsError) {
        return new TripOptionsState(
                tripId, destination, date, startTime, endTime, feedback, feedbackIsError);
    }

    public String getTripId() {
        return tripId;
    }

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
