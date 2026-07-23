package closeai.application.usecases;

import closeai.application.ports.TripRepository;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OptimizeItineraryInteractorTest {

    @Test
    void compactsOnlyCurrentActivitiesAndPreservesTripState() {
        Trip trip = trip("trip-1", LocalTime.of(9, 0), LocalTime.of(18, 0));
        Activity first = activity("first", LocalTime.of(9, 0), LocalTime.of(18, 0));
        Activity second = activity("second", LocalTime.of(9, 0), LocalTime.of(18, 0));
        Activity unrelatedBookmark = activity(
                "bookmark-only", LocalTime.of(9, 0), LocalTime.of(18, 0));
        trip.bookmark(unrelatedBookmark);
        trip.addEvent(travel("travel-before", LocalTime.of(9, 0), LocalTime.of(9, 20)));
        trip.addEvent(event("event-first", first, LocalTime.of(10, 0), LocalTime.of(11, 0)));
        trip.addEvent(travel("travel-middle", LocalTime.of(11, 0), LocalTime.of(11, 20)));
        trip.addEvent(event("event-second", second, LocalTime.of(13, 0), LocalTime.of(14, 30)));
        FakeTripRepository repository = new FakeTripRepository(trip);
        RecordingOutputBoundary output = new RecordingOutputBoundary();

        new OptimizeItineraryInteractor(repository, output)
                .execute(new OptimizeItineraryInputData(trip.getId()));

        Trip saved = repository.saved;
        assertNotNull(saved);
        assertNotNull(output.success);
        assertNull(output.failure);
        assertEquals(trip.getId(), saved.getId());
        assertEquals("Toronto", saved.getDestination());
        assertEquals(LocalDate.of(2026, 7, 23), saved.getDate());
        assertEquals(LocalTime.of(9, 0), saved.getStartTime());
        assertEquals(LocalTime.of(18, 0), saved.getEndTime());
        assertEquals(TransportationMode.TRANSIT, saved.getTransportationMode());
        assertEquals(List.of("bookmark-only"), ids(saved.getBookmarkedActivities()));
        assertEquals(List.of("event-first", "event-second"),
                saved.getScheduledEvents().stream()
                        .map(ScheduledEvent::getId)
                        .collect(Collectors.toList()));
        assertEquals(List.of("first", "second"),
                saved.getScheduledEvents().stream()
                        .map(event -> event.getActivity().getId())
                        .collect(Collectors.toList()));
        assertEquals(LocalTime.of(9, 0), saved.getScheduledEvents().get(0).getStartTime());
        assertEquals(LocalTime.of(10, 0), saved.getScheduledEvents().get(0).getEndTime());
        assertEquals(LocalTime.of(10, 0), saved.getScheduledEvents().get(1).getStartTime());
        assertEquals(LocalTime.of(11, 30), saved.getScheduledEvents().get(1).getEndTime());
        assertEquals("Keep first notes", saved.getScheduledEvents().get(0).getNotes());
        assertEquals("Keep second notes", saved.getScheduledEvents().get(1).getNotes());
        assertEquals(saved, output.success.getTrip());
    }

    @Test
    void reportsFailureWithoutSavingWhenNoActivitiesAreScheduled() {
        Trip trip = trip("empty", LocalTime.of(9, 0), LocalTime.of(18, 0));
        trip.bookmark(activity("bookmark-only", LocalTime.of(9, 0), LocalTime.of(18, 0)));
        trip.addEvent(travel("travel-only", LocalTime.of(9, 0), LocalTime.of(9, 20)));
        FakeTripRepository repository = new FakeTripRepository(trip);
        RecordingOutputBoundary output = new RecordingOutputBoundary();

        new OptimizeItineraryInteractor(repository, output)
                .execute(new OptimizeItineraryInputData(trip.getId()));

        assertNull(repository.saved);
        assertNull(output.success);
        assertTrue(output.failure.contains("Add activities"));
    }

    @Test
    void reportsFailureWithoutSavingWhenCompactedActivityCannotFit() {
        Trip trip = trip("cannot-fit", LocalTime.of(9, 0), LocalTime.of(11, 0));
        Activity closesEarly = activity(
                "closes-early", LocalTime.of(9, 0), LocalTime.of(10, 0));
        trip.addEvent(event(
                "too-long", closesEarly, LocalTime.of(9, 0), LocalTime.of(10, 30)));
        FakeTripRepository repository = new FakeTripRepository(trip);
        RecordingOutputBoundary output = new RecordingOutputBoundary();

        new OptimizeItineraryInteractor(repository, output)
                .execute(new OptimizeItineraryInputData(trip.getId()));

        assertNull(repository.saved);
        assertNull(output.success);
        assertFalse(output.failure.isEmpty());
        assertTrue(output.failure.contains("cannot be compacted"));
    }

    private Trip trip(String id, LocalTime start, LocalTime end) {
        return new Trip(id, "Toronto", LocalDate.of(2026, 7, 23), start, end,
                TransportationMode.TRANSIT);
    }

    private Activity activity(String id, LocalTime opening, LocalTime closing) {
        return new Activity(id, id, ActivityCategory.ATTRACTION,
                new Location(43.65, -79.38, id), 4.5, 60, opening, closing,
                IndoorOutdoorType.INDOOR, "Low");
    }

    private ScheduledEvent event(
            String id, Activity activity, LocalTime start, LocalTime end) {
        String notes = "event-first".equals(id) ? "Keep first notes" : "Keep second notes";
        return new ScheduledEvent(id, activity, start, end, EventType.ACTIVITY, notes);
    }

    private ScheduledEvent travel(String id, LocalTime start, LocalTime end) {
        return new ScheduledEvent(id, null, start, end, EventType.TRAVEL, "Travel");
    }

    private List<String> ids(List<Activity> activities) {
        List<String> ids = new ArrayList<String>();
        for (Activity activity : activities) {
            ids.add(activity.getId());
        }
        return ids;
    }

    private static final class FakeTripRepository implements TripRepository {
        private final Trip original;
        private Trip saved;

        private FakeTripRepository(Trip original) {
            this.original = original;
        }

        @Override
        public Trip save(Trip trip) {
            saved = trip;
            return trip;
        }

        @Override
        public Optional<Trip> findById(String id) {
            return original.getId().equals(id) ? Optional.of(original) : Optional.empty();
        }
    }

    private static final class RecordingOutputBoundary
            implements OptimizeItineraryOutputBoundary {
        private OptimizeItineraryOutputData success;
        private String failure;

        @Override
        public void presentSuccess(OptimizeItineraryOutputData outputData) {
            success = outputData;
        }

        @Override
        public void presentFailure(String errorMessage) {
            failure = errorMessage;
        }
    }
}
