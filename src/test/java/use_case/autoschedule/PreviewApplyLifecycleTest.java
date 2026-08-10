package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.EventType;
import entity.valueobjects.TransportationMode;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import use_case.autoschedule.testdoubles.FakeWeatherContextGateway;
import use_case.autoschedule.testdoubles.RecordingPresenter;

/**
 * The Preview → Apply lifecycle, asserted on the stored Trip rather than on the screen.
 *
 * <p>A user reported a Day Plan that had become incoherent after Apply. These cover the
 * contract that keeps it coherent: Preview writes nothing, Apply replaces rather than
 * appends, every activity survives exactly once, travel is generated between activities
 * only, and a second run never treats the previous run's travel as something to schedule.</p>
 */
class PreviewApplyLifecycleTest {

    private final RecordingPresenter presenter = new RecordingPresenter();
    private final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator();
    private final FakeWeatherContextGateway weather = new FakeWeatherContextGateway();
    private FakeTripRepository trips;

    private static ScheduledEvent activityEvent(String id, int startHour) {
        final Activity activity = ProblemFixtures.activity(id, LocalTime.of(9, 0), LocalTime.of(21, 0));
        return new ScheduledEvent(id, activity, LocalTime.of(startHour, 0),
                LocalTime.of(startHour + 1, 0), EventType.ACTIVITY, "");
    }

    @BeforeEach
    void setUp() {
        final Trip trip = new Trip("trip-1", "Toronto", ProblemFixtures.TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(
                activityEvent("a", 9), activityEvent("b", 13), activityEvent("c", 17)));
        trips = new FakeTripRepository(trip);
    }

    private AutoScheduleInteractor interactor() {
        return new AutoScheduleInteractor(trips, estimator, weather, presenter,
                Arrays.asList(new WeatherSuitabilityPolicy(), new MealWindowPolicy(),
                        new DaylightPolicy()),
                new ScheduleEngine());
    }

    private AutoScheduleInputData previewInput() {
        return new AutoScheduleInputData("trip-1", LocalTime.of(9, 0), LocalTime.of(21, 0),
                TransportationMode.WALKING, Collections.<String>emptySet(),
                Collections.<TimeWindow>emptyList(), true, false);
    }

    private static List<ScheduledEvent> eventsOf(Trip trip, EventType type) {
        final List<ScheduledEvent> found = new ArrayList<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getEventType() == type) {
                found.add(event);
            }
        }
        return found;
    }

    private AutoScheduleApplyInputData applyInputFrom(AutoSchedulePreviewOutputData preview) {
        return new AutoScheduleApplyInputData("trip-1", preview.getScheduleFingerprint(),
                preview.getRows());
    }

    @Test
    void previewDoesNotTouchTheStoredTrip() {
        final List<ScheduledEvent> before =
                new ArrayList<>(trips.findById("trip-1").orElseThrow().getScheduledEvents());

        interactor().preview(previewInput());

        assertNotNull(presenter.getPreview());
        assertEquals(0, trips.getSaveCount(), "a Preview must never write");
        assertEquals(before, trips.findById("trip-1").orElseThrow().getScheduledEvents());
    }

    @Test
    void repeatedPreviewsDoNotAccumulateRows() {
        final AutoScheduleInteractor interactor = interactor();
        interactor.preview(previewInput());
        final int first = presenter.getPreview().getRows().size();

        interactor.preview(previewInput());
        interactor.preview(previewInput());

        assertEquals(first, presenter.getPreview().getRows().size(),
                "each Preview replaces the last; rows must not pile up");
        assertEquals(0, trips.getSaveCount());
    }

    @Test
    void applyKeepsEveryActivityExactlyOnce() {
        final AutoScheduleInteractor interactor = interactor();
        interactor.preview(previewInput());
        interactor.apply(applyInputFrom(presenter.getPreview()));

        final Trip saved = trips.findById("trip-1").orElseThrow();
        final List<ScheduledEvent> activities = eventsOf(saved, EventType.ACTIVITY);
        final Set<String> ids = new HashSet<>();
        for (ScheduledEvent event : activities) {
            assertTrue(ids.add(event.getId()), "duplicated activity " + event.getId());
        }
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "c")), ids);
        assertEquals(3, activities.size());
    }

    @Test
    void applyGeneratesTravelOnlyBetweenActivitiesNeverBeforeOrAfter() {
        final AutoScheduleInteractor interactor = interactor();
        interactor.preview(previewInput());
        interactor.apply(applyInputFrom(presenter.getPreview()));

        final List<ScheduledEvent> all = trips.findById("trip-1").orElseThrow().getScheduledEvents();
        assertEquals(EventType.ACTIVITY, all.get(0).getEventType(),
                "no journey before the first activity: the trip has no origin");
        assertEquals(EventType.ACTIVITY, all.get(all.size() - 1).getEventType(),
                "no journey after the last activity: the trip has no destination");
        final long travel = all.stream().filter(e -> e.getEventType() == EventType.TRAVEL).count();
        assertTrue(travel <= 2, "at most one journey between each consecutive pair, got " + travel);
    }

    @Test
    void applyReplacesTheScheduleRatherThanAppendingToIt() {
        final AutoScheduleInteractor interactor = interactor();
        interactor.preview(previewInput());
        interactor.apply(applyInputFrom(presenter.getPreview()));
        final int afterFirst = trips.findById("trip-1").orElseThrow().getScheduledEvents().size();

        interactor.preview(previewInput());
        interactor.apply(applyInputFrom(presenter.getPreview()));

        assertEquals(afterFirst,
                trips.findById("trip-1").orElseThrow().getScheduledEvents().size(),
                "applying again must not grow the day");
        assertEquals(3, eventsOf(trips.findById("trip-1").orElseThrow(),
                EventType.ACTIVITY).size());
    }

    @Test
    void asecondRunSchedulesActivitiesOnlyAndIgnoresGeneratedTravel() {
        final AutoScheduleInteractor interactor = interactor();
        interactor.preview(previewInput());
        interactor.apply(applyInputFrom(presenter.getPreview()));
        assertFalse(eventsOf(trips.findById("trip-1").orElseThrow(), EventType.TRAVEL).isEmpty(),
                "precondition: the applied day contains travel");

        interactor.preview(previewInput());

        final long activityRows = presenter.getPreview().getRows().stream()
                .filter(r -> r.getKind() == ProposedEventData.Kind.ACTIVITY).count();
        assertEquals(3, activityRows,
                "the previous run's travel must not become an activity to schedule");
    }

    @Test
    void applyIsRefusedWhenTheDayPlanChangedAfterThePreview() {
        final AutoScheduleInteractor interactor = interactor();
        interactor.preview(previewInput());
        final AutoScheduleApplyInputData stale = applyInputFrom(presenter.getPreview());

        final Trip trip = trips.findById("trip-1").orElseThrow();
        final List<ScheduledEvent> shorter = new ArrayList<>(trip.getScheduledEvents());
        shorter.remove(2);
        trips.save(trip.copyWithSchedule(shorter));
        final int savesBefore = trips.getSaveCount();

        interactor.apply(stale);

        assertNotNull(presenter.getFailure(), "a stale Preview must be refused");
        assertEquals(savesBefore, trips.getSaveCount(), "and must not write anything");
    }
}
