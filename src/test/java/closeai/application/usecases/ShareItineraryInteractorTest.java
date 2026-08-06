package closeai.application.usecases;

import closeai.application.ports.ItineraryPngExporter;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import closeai.infrastructure.persistence.InMemoryItineraryDataAccessObject;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShareItineraryInteractorTest {

    @Test
    void executeExportsPngForScheduledItinerary() {
        InMemoryItineraryDataAccessObject trips = new InMemoryItineraryDataAccessObject();
        Trip trip = tripWithEvent();
        trips.save(trip);

        RecordingExporter exporter = new RecordingExporter(new byte[] {1, 2, 3, 4});
        RecordingPresenter presenter = new RecordingPresenter();
        ShareItineraryInteractor interactor =
                new ShareItineraryInteractor(trips, exporter, presenter);

        interactor.execute(new ShareItineraryInputData(trip.getId()));

        assertTrue(exporter.called);
        assertEquals("Toronto", exporter.lastCard.getDestination());
        assertEquals(1, exporter.lastCard.getLines().size());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, presenter.success.getPngBytes());
        assertEquals("CloseAI-Toronto-2026-08-07.png", presenter.success.getSuggestedFileName());
        assertNull(presenter.failure);
    }

    @Test
    void executeRejectsEmptyScheduleWithoutCallingExporter() {
        InMemoryItineraryDataAccessObject trips = new InMemoryItineraryDataAccessObject();
        Trip trip = new Trip("empty", "Toronto", LocalDate.of(2026, 8, 7),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
        trips.save(trip);

        RecordingExporter exporter = new RecordingExporter(new byte[] {9});
        RecordingPresenter presenter = new RecordingPresenter();
        ShareItineraryInteractor interactor =
                new ShareItineraryInteractor(trips, exporter, presenter);

        interactor.execute(new ShareItineraryInputData("empty"));

        assertFalse(exporter.called);
        assertEquals("Add activities to the Day Plan before sharing", presenter.failure);
        assertNull(presenter.success);
    }

    private static Trip tripWithEvent() {
        Trip trip = new Trip("share-1", "Toronto", LocalDate.of(2026, 8, 7),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
        Activity museum = new Activity("museum", "Royal Ontario Museum", ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, "Downtown"), 4.5, 90,
                LocalTime.of(10, 0), LocalTime.of(17, 0), IndoorOutdoorType.INDOOR, "low");
        trip.addEvent(new ScheduledEvent("evt-1", museum, LocalTime.of(10, 0), LocalTime.of(11, 30),
                EventType.ACTIVITY, "Morning visit"));
        return trip;
    }

    private static final class RecordingExporter implements ItineraryPngExporter {
        private final byte[] bytes;
        private boolean called;
        private ShareCardModel lastCard;

        private RecordingExporter(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public byte[] export(ShareCardModel card) {
            called = true;
            lastCard = card;
            return bytes;
        }
    }

    private static final class RecordingPresenter implements ShareItineraryOutputBoundary {
        private ShareItineraryOutputData success;
        private String failure;

        @Override
        public void presentSuccess(ShareItineraryOutputData outputData) {
            success = outputData;
        }

        @Override
        public void presentFailure(String errorMessage) {
            failure = errorMessage;
        }
    }
}
