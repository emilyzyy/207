package use_case.usecases;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import entity.valueobjects.TransportationMode;

/** Immutable input for the Create Trip interactor. */
public final class CreateTripInputData {
    private final String destination;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final TransportationMode transportationMode;
    private final int dayCount;
    private final List<String> companionIds;

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
        this(destination, date, startTime, endTime, transportationMode, dayCount, null);
    }

    public CreateTripInputData(
            String destination,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            TransportationMode transportationMode,
            int dayCount,
            List<String> companionIds) {
        this.destination = destination;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.transportationMode = transportationMode;
        this.dayCount = dayCount;
        this.companionIds = companionIds;
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

    public List<String> getCompanionIds() {
        return companionIds;
    }
}
