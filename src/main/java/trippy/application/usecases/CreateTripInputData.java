package trippy.application.usecases;

import trippy.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;

/** Immutable input for the Create Trip interactor. */
public final class CreateTripInputData {
    private final String destination;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final TransportationMode transportationMode;
    private final int dayCount;

    public CreateTripInputData(
            String destination,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            TransportationMode transportationMode) {
        this(destination, date, startTime, endTime, transportationMode, 1);
    }

    public CreateTripInputData(
            String destination,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            TransportationMode transportationMode,
            int dayCount) {
        this.destination = destination;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.transportationMode = transportationMode;
        this.dayCount = dayCount;
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

    public TransportationMode getTransportationMode() {
        return transportationMode;
    }

    public int getDayCount() {
        return dayCount;
    }
}
