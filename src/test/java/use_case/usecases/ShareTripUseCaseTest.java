package use_case.usecases;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import database.persistence.InMemoryItineraryDataAccessObject;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShareTripUseCaseTest {

    @Test
    void createsPortableSummaryWithTripOptionsAndSchedule() {
        InMemoryItineraryDataAccessObject trips = new InMemoryItineraryDataAccessObject();
        Trip trip = trip("trip-share");
        Activity museum = new Activity(
                "rom", "Royal Ontario Museum", ActivityCategory.MUSEUM,
                new Location(43.6677, -79.3948, "100 Queens Park"),
                4.7, 90, LocalTime.of(10, 0), LocalTime.of(17, 30),
                IndoorOutdoorType.INDOOR, "Low");
        trip.addEvent(new ScheduledEvent(
                "event-rom", museum, LocalTime.of(10, 0), LocalTime.of(11, 30),
                EventType.ACTIVITY, "Visit exhibits"));
        trips.save(trip);

        ShareTripUseCase useCase = new ShareTripUseCase(
                new GetTripSummaryUseCase(trips));

        String shared = useCase.execute("trip-share");

        assertTrue(shared.contains("Trippy trip to Toronto"));
        assertTrue(shared.contains("Date: 2026-08-12"));
        assertTrue(shared.contains("Transportation: TRANSIT"));
        assertTrue(shared.contains("10:00 AM – 11:30 AM · Royal Ontario Museum"));
        assertTrue(shared.endsWith("Shared from Trippy"));
    }

    @Test
    void rejectsMissingActiveTripAndUnknownTrip() {
        ShareTripUseCase useCase = new ShareTripUseCase(
                new GetTripSummaryUseCase(new InMemoryItineraryDataAccessObject()));

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(" "));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("missing"));
    }

    private Trip trip(String id) {
        return new Trip(
                id, "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                TransportationMode.TRANSIT);
    }
}
