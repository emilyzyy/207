package closeai.adapters.viewmodels;

import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;

/** Immutable seeded trip-options display state. */
public final class TripOptionsState {
    private final String destination;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final TransportationMode transportationMode;

    public TripOptionsState(
            String destination, LocalDate date, LocalTime startTime,
            LocalTime endTime, TransportationMode transportationMode) {
        this.destination = destination == null ? "" : destination;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.transportationMode = transportationMode;
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
}
