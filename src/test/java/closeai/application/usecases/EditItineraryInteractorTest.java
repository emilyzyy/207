package closeai.application.usecases;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EditItineraryInteractorTest {

    @Test
    void executeUpdatesExistingItineraryOptionsAndPersistsThem() {
        InMemoryItineraryDataAccessObject dataAccess = new InMemoryItineraryDataAccessObject();
        Trip itinerary = new Trip("itin-1", "Toronto", LocalDate.of(2026, 7, 23),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
        Activity museum = new Activity("museum", "Museum", ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, "Downtown"), 4.5, 90,
                LocalTime.of(10, 0), LocalTime.of(17, 0), IndoorOutdoorType.INDOOR, "low");
        itinerary.addEvent(new ScheduledEvent("evt-1", museum, LocalTime.of(10, 0), LocalTime.of(11, 30),
                EventType.ACTIVITY, "Morning visit"));
        dataAccess.saveItinerary(itinerary);

        EditItineraryInputBoundary interactor = new EditItineraryInteractor(dataAccess);
        Trip updated = interactor.execute(new EditItineraryInputData(
                "itin-1", "Montreal", LocalDate.of(2026, 8, 1),
                LocalTime.of(8, 0), LocalTime.of(20, 0), TransportationMode.TRANSIT));

        assertEquals("Montreal", updated.getDestination());
        assertEquals(LocalDate.of(2026, 8, 1), updated.getDate());
        assertEquals(LocalTime.of(8, 0), updated.getStartTime());
        assertEquals(LocalTime.of(20, 0), updated.getEndTime());
        assertEquals(TransportationMode.TRANSIT, updated.getTransportationMode());
        assertEquals(1, updated.getScheduledEvents().size());

        Trip reloaded = dataAccess.loadItinerary("itin-1").orElseThrow();
        assertEquals("Montreal", reloaded.getDestination());
        assertEquals(TransportationMode.TRANSIT, reloaded.getTransportationMode());
    }
}
