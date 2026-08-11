package use_case.tripassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import database.persistence.CachedPlacesRepository;
import database.persistence.InMemoryTripRepository;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.WeatherSeverity;
import use_case.ports.TripAssistantGateway;

final class TripAssistantInteractorTest {

    @Test
    void suppliesCompleteCurrentTripContextAndRemovesUnknownActivityIds() {
        final Activity museum = activity("museum", "Actual Museum", IndoorOutdoorType.INDOOR, 4.8);
        final Activity park = activity("park", "Actual Park", IndoorOutdoorType.OUTDOOR, 4.6);
        final Trip trip = trip();
        trip.setDiscoveredPlaces(Arrays.asList(museum, park));
        trip.bookmark(museum);
        trip.addEvent(new ScheduledEvent(
                "event-park", park, LocalTime.of(14, 0), LocalTime.of(15, 0),
                EventType.ACTIVITY, "Visit"));
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(trip);
        final CachedPlacesRepository activities = new CachedPlacesRepository();
        activities.addAll(Arrays.asList(museum, park));
        final CapturingGateway gateway = new CapturingGateway();
        final RecordingPresenter presenter = new RecordingPresenter();
        final TripAssistantInteractor interactor = new TripAssistantInteractor(
                trips, activities,
                ignored -> {
                    return Collections.singletonList(new WeatherWarning(
                            new Location(43.6, -79.3, "Toronto"), LocalTime.of(13, 0),
                            "Rain", WeatherSeverity.MEDIUM, "Wet afternoon"));
                },
                gateway, presenter);

        interactor.execute(new TripAssistantInputData(
                trip.getId(), "What should I do if it rains?", Collections.emptyList()));

        final TripAssistantRequest context = gateway.request;
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
        final CapturingGateway gateway = new CapturingGateway();
        final RecordingPresenter presenter = new RecordingPresenter();
        final TripAssistantInteractor interactor = new TripAssistantInteractor(
                new InMemoryTripRepository(), new CachedPlacesRepository(),
                ignored -> Collections.emptyList(), gateway, presenter);

        interactor.execute(new TripAssistantInputData(
                "missing", "Recommend something", Collections.emptyList()));

        assertEquals("The current trip could not be found", presenter.failure);
        assertEquals(null, gateway.request);
    }

    @Test
    void rejectsBlankQuestionOrTripAndNullDependencies() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TripAssistantInteractor(
                    null, new CachedPlacesRepository(), ignored -> Collections.emptyList(),
                    ignored -> null, new RecordingPresenter());
        });

        final RecordingPresenter presenter = new RecordingPresenter();
        final TripAssistantInteractor interactor = new TripAssistantInteractor(
                new InMemoryTripRepository(), new CachedPlacesRepository(),
                ignored -> Collections.emptyList(), ignored -> null, presenter);

        interactor.execute(new TripAssistantInputData("trip-1", "  ", Collections.emptyList()));
        assertEquals("Type a question for George", presenter.failure);

        interactor.execute(new TripAssistantInputData(" ", "Hello", Collections.emptyList()));
        assertEquals("Open or create a trip before asking George", presenter.failure);
    }

    @Test
    void weatherFailureUsesEmptyWarningsAndGatewayRuntimeBecomesFriendlyFailure() {
        final Activity museum = activity("museum", "Actual Museum", IndoorOutdoorType.INDOOR, 4.8);
        final Trip trip = trip();
        // No discovered places: falls back to the activity repository catalogue.
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(trip);
        final CachedPlacesRepository activities = new CachedPlacesRepository();
        activities.addAll(Collections.singletonList(museum));
        final CapturingGateway gateway = new CapturingGateway();
        final RecordingPresenter presenter = new RecordingPresenter();
        final TripAssistantInteractor interactor = new TripAssistantInteractor(
                trips, activities,
                ignored -> {
                    throw new IllegalStateException("weather down");
                },
                gateway, presenter);

        interactor.execute(new TripAssistantInputData(
                trip.getId(), "Any ideas?", Collections.emptyList()));

        assertTrue(gateway.request.getWeather().isEmpty());
        assertEquals(1, gateway.request.getActivities().size());

        final TripAssistantInteractor failing = new TripAssistantInteractor(
                trips, activities, ignored -> Collections.emptyList(),
                ignored -> {
                    throw new RuntimeException("provider offline");
                },
                presenter);
        failing.execute(new TripAssistantInputData(
                trip.getId(), "Any ideas?", Collections.emptyList()));
        assertEquals("George couldn't answer right now. Please try again.", presenter.failure);
    }

    @Test
    void displaysLiveGeneralAnswerWithoutInventingAnActivity() {
        final Activity museum = activity("museum", "Actual Museum", IndoorOutdoorType.INDOOR, 4.8);
        final Trip trip = trip();
        trip.setDiscoveredPlaces(Collections.singletonList(museum));
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(trip);
        final CachedPlacesRepository activities = new CachedPlacesRepository();
        activities.addAll(Collections.singletonList(museum));
        final RecordingPresenter presenter = new RecordingPresenter();
        final TripAssistantGateway gateway = ignored -> {
            return new TripAssistantDecision(
                    TripAssistantDecision.Intent.GENERAL, Collections.emptyList(),
                    "I'm George, and 3 + 3 is 6.", "");
        };
        final TripAssistantInteractor interactor = new TripAssistantInteractor(
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
        public void presentSuccess(TripAssistantOutputData value) {
            output = value;
        }

        @Override
        public void presentFailure(String message) {
            failure = message;
        }
    }
}
