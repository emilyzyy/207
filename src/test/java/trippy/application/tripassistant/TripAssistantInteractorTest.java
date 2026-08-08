package trippy.application.tripassistant;

import trippy.application.ports.TripAssistantGateway;
import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.Trip;
import trippy.domain.entities.WeatherWarning;
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.EventType;
import trippy.domain.valueobjects.IndoorOutdoorType;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import trippy.domain.valueobjects.WeatherSeverity;
import trippy.infrastructure.persistence.CachedPlacesRepository;
import trippy.infrastructure.persistence.InMemoryTripRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TripAssistantInteractorTest {

    @Test
    void suppliesCompleteCurrentTripContextAndRemovesUnknownActivityIds() {
        Activity museum = activity("museum", "Actual Museum", IndoorOutdoorType.INDOOR, 4.8);
        Activity park = activity("park", "Actual Park", IndoorOutdoorType.OUTDOOR, 4.6);
        Trip trip = trip();
        trip.setDiscoveredPlaces(Arrays.asList(museum, park));
        trip.bookmark(museum);
        trip.addEvent(new ScheduledEvent(
                "event-park", park, LocalTime.of(14, 0), LocalTime.of(15, 0),
                EventType.ACTIVITY, "Visit"));
        InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(trip);
        CachedPlacesRepository activities = new CachedPlacesRepository();
        activities.addAll(Arrays.asList(museum, park));
        CapturingGateway gateway = new CapturingGateway();
        RecordingPresenter presenter = new RecordingPresenter();
        TripAssistantInteractor interactor = new TripAssistantInteractor(
                trips, activities,
                ignored -> Collections.singletonList(new WeatherWarning(
                        new Location(43.6, -79.3, "Toronto"), LocalTime.of(13, 0),
                        "Rain", WeatherSeverity.MEDIUM, "Wet afternoon")),
                gateway, presenter);

        interactor.execute(new TripAssistantInputData(
                trip.getId(), "What should I do if it rains?", Collections.emptyList()));

        TripAssistantRequest context = gateway.request;
        assertEquals("Toronto", context.getDestination());
        assertEquals(LocalDate.of(2026, 8, 20), context.getDate());
        assertEquals(LocalTime.of(9, 0), context.getStartTime());
        assertEquals(LocalTime.of(18, 0), context.getEndTime());
        assertEquals(TransportationMode.TRANSIT, context.getTransportationMode());
        assertEquals(2, context.getActivities().size());
        assertTrue(context.getBookmarkedActivityIds().contains("museum"));
        assertEquals("park", context.getScheduledEvents().get(0).getActivity().getId());
        assertEquals("Rain", context.getWeather().get(0).getWeatherCondition());
        assertEquals(Collections.singletonList("museum"), presenter.output.getActivityIds());
        assertTrue(presenter.output.getAnswer().contains("Actual Museum"));
        assertFalse(presenter.output.getAnswer().contains("invented-place"));
    }

    @Test
    void missingTripProducesClearFailureWithoutCallingGateway() {
        CapturingGateway gateway = new CapturingGateway();
        RecordingPresenter presenter = new RecordingPresenter();
        TripAssistantInteractor interactor = new TripAssistantInteractor(
                new InMemoryTripRepository(), new CachedPlacesRepository(),
                ignored -> Collections.emptyList(), gateway, presenter);

        interactor.execute(new TripAssistantInputData(
                "missing", "Recommend something", Collections.emptyList()));

        assertEquals("The current trip could not be found", presenter.failure);
        assertEquals(null, gateway.request);
    }

    @Test
    void displaysLiveGeneralAnswerWithoutInventingAnActivity() {
        Activity museum = activity("museum", "Actual Museum", IndoorOutdoorType.INDOOR, 4.8);
        Trip trip = trip();
        trip.setDiscoveredPlaces(Collections.singletonList(museum));
        InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(trip);
        CachedPlacesRepository activities = new CachedPlacesRepository();
        activities.addAll(Collections.singletonList(museum));
        RecordingPresenter presenter = new RecordingPresenter();
        TripAssistantGateway gateway = ignored -> new TripAssistantDecision(
                TripAssistantDecision.Intent.GENERAL, Collections.emptyList(),
                "I'm George, and 3 + 3 is 6.", "");
        TripAssistantInteractor interactor = new TripAssistantInteractor(
                trips, activities, ignored -> Collections.emptyList(), gateway, presenter);

        interactor.execute(new TripAssistantInputData(
                trip.getId(), "What is your name, and what is 3 + 3?",
                Collections.emptyList()));

        assertEquals("I'm George, and 3 + 3 is 6.", presenter.output.getAnswer());
        assertTrue(presenter.output.getActivityIds().isEmpty());
    }

    private Trip trip() {
        return new Trip("trip-1", "Toronto", LocalDate.of(2026, 8, 20),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.TRANSIT);
    }

    private Activity activity(
            String id, String name, IndoorOutdoorType type, double rating) {
        return new Activity(id, name, ActivityCategory.ATTRACTION,
                new Location(43.6, -79.3, name + " address"), rating, 60,
                LocalTime.of(9, 0), LocalTime.of(20, 0), type, "Low");
    }

    private static final class CapturingGateway implements TripAssistantGateway {
        private TripAssistantRequest request;

        @Override
        public TripAssistantDecision answer(TripAssistantRequest value) {
            request = value;
            return new TripAssistantDecision(
                    TripAssistantDecision.Intent.RAIN,
                    Arrays.asList("museum", "invented-place"),
                    "Visit invented-place because it is perfect.", "");
        }
    }

    private static final class RecordingPresenter implements TripAssistantOutputBoundary {
        private TripAssistantOutputData output;
        private String failure;

        @Override
        public void presentSuccess(TripAssistantOutputData value) { output = value; }

        @Override
        public void presentFailure(String message) { failure = message; }
    }
}
