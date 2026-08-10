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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShareTripUseCaseTest {

    @Test
    void presentsPortableSummaryWithTripOptionsAndSchedule() {
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

        RecordingOutput output = new RecordingOutput();
        ShareTripUseCase useCase = new ShareTripUseCase(
                new GetTripSummaryUseCase(trips), trips, output);

        useCase.execute("trip-share");

        assertNull(output.failure);
        assertTrue(output.success.getShareText().contains("Trippy trip to Toronto"));
        assertTrue(output.success.getShareText().contains("Date: 2026-08-12"));
        assertTrue(output.success.getShareText().contains("10:00 AM – 11:30 AM · Royal Ontario Museum"));
        assertEquals("trip-share", output.success.getTrip().getId());
    }

    @Test
    void presentsFailureForMissingActiveTripAndUnknownTrip() {
        RecordingOutput output = new RecordingOutput();
        ShareTripUseCase useCase = new ShareTripUseCase(
                new GetTripSummaryUseCase(new InMemoryItineraryDataAccessObject()),
                new InMemoryItineraryDataAccessObject(),
                output);

        useCase.execute(" ");
        assertEquals("Create a trip before sharing", output.failure);

        useCase.execute(null);
        assertEquals("Create a trip before sharing", output.failure);

        useCase.execute("missing");
        assertEquals("Trip not found", output.failure);
    }

    @Test
    void rejectsNullDependencies() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ShareTripUseCase(null, new InMemoryItineraryDataAccessObject(),
                        new RecordingOutput()));
    }

    @Test
    void executeAndReturnKeepsRestCompatibility() {
        InMemoryItineraryDataAccessObject trips = new InMemoryItineraryDataAccessObject();
        trips.save(trip("trip-share"));
        ShareTripUseCase useCase = new ShareTripUseCase(
                new GetTripSummaryUseCase(trips), trips, new RecordingOutput());

        String shared = useCase.executeAndReturn("trip-share");

        assertTrue(shared.contains("Trippy trip to Toronto"));
        assertTrue(shared.endsWith("Shared from Trippy"));
        assertFalse(shared.isEmpty());
    }

    private Trip trip(String id) {
        return new Trip(
                id, "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                TransportationMode.TRANSIT);
    }

    private static final class RecordingOutput implements ShareTripOutputBoundary {
        private ShareTripOutputData success;
        private String failure;

        @Override
        public void presentSuccess(ShareTripOutputData outputData) {
            success = outputData;
            failure = null;
        }

        @Override
        public void presentFailure(String errorMessage) {
            failure = errorMessage;
            success = null;
        }
    }
}
