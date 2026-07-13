package closeai.application.usecases;

import closeai.application.ports.TripRepository;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class CreateTripUseCase {
    private final TripRepository trips;
    public CreateTripUseCase(TripRepository trips) { this.trips = trips; }
    public Trip execute(String destination, LocalDate date, LocalTime start, LocalTime end,
                        TransportationMode mode) {
        if (destination == null || destination.trim().isEmpty()) throw new IllegalArgumentException("Destination is required");
        return trips.save(new Trip(UUID.randomUUID().toString(), destination.trim(), date, start, end, mode));
    }
}
