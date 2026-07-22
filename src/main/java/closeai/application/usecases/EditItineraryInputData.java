package closeai.application.usecases;

import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Immutable input for editing an already-created itinerary's trip options.
 */
public final class EditItineraryInputData {
    private final String itineraryId;
    private final String destination;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final TransportationMode transportationMode;

    public EditItineraryInputData(String itineraryId, String destination, LocalDate date,
                                  LocalTime startTime, LocalTime endTime,
                                  TransportationMode transportationMode) {
        this.itineraryId = itineraryId;
        this.destination = destination;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.transportationMode = transportationMode;
    }

    public String getItineraryId() {
        return itineraryId;
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
