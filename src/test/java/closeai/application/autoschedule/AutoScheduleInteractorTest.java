package closeai.application.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.application.autoschedule.engine.ScheduleEngine;
import closeai.application.autoschedule.policy.DaylightPolicy;
import closeai.application.autoschedule.policy.MealWindowPolicy;
import closeai.application.autoschedule.policy.SoftPolicy;
import closeai.application.autoschedule.policy.WeatherSuitabilityPolicy;
import closeai.application.autoschedule.testdoubles.FakeTravelTimeEstimator;
import closeai.application.autoschedule.testdoubles.FakeTripRepository;
import closeai.application.autoschedule.testdoubles.FakeWeatherContextGateway;
import closeai.application.autoschedule.testdoubles.RecordingPresenter;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.TransportationMode;
import closeai.domain.valueobjects.WeatherSeverity;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AutoScheduleInteractorTest {

    private static final List<SoftPolicy> REGISTERED = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private final RecordingPresenter presenter = new RecordingPresenter();
    private final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator();
    private final FakeWeatherContextGateway weather = new FakeWeatherContextGateway();

    private static Trip tripWith(ScheduledEvent... events) {
        Trip trip = new Trip("trip-1", "Toronto", ProblemFixtures.TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(events));
        return trip;
    }

    private static ScheduledEvent activityEvent(String id, int startHour, int durationMinutes) {
        Activity activity = ProblemFixtures.activity(id, LocalTime.of(9, 0), LocalTime.of(21, 0));
        LocalTime start = LocalTime.of(startHour, 0);
        return new ScheduledEvent(id, activity, start, start.plusMinutes(durationMinutes),
                EventType.ACTIVITY, "");
    }

    private AutoScheduleInteractor interactorFor(FakeTripRepository trips) {
        return new AutoScheduleInteractor(trips, estimator, weather, presenter,
                REGISTERED, new ScheduleEngine());
    }

    private static AutoScheduleInputData input(String tripId, Set<String> locks,
                                               List<TimeWindow> unavailable,
                                               boolean keepCurrentOrder) {
        return new AutoScheduleInputData(tripId, LocalTime.of(9, 0), LocalTime.of(21, 0),
                TransportationMode.WALKING, locks, unavailable, keepCurrentOrder);
    }

    private static AutoScheduleInputData simpleInput() {
        return input("trip-1", Collections.emptySet(), Collections.emptyList(), true);
    }

    @Test
    void previewProducesRowsWithoutSavingAnything() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(activityEvent("a", 9, 60), activityEvent("b", 12, 60)));

        interactorFor(trips).preview(simpleInput());

        assertNotNull(presenter.getPreview(), "expected a preview");
        assertNull(presenter.getFailure());
        assertEquals(0, trips.getSaveCount(), "a preview must never write to the repository");
    }

    @Test
    void previewKeepsEveryActivityExactlyOnce() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(
                activityEvent("a", 9, 60), activityEvent("b", 11, 60), activityEvent("c", 14, 60)));

        interactorFor(trips).preview(simpleInput());

        List<String> activityIds = new ArrayList<>();
        for (ProposedEventData row : presenter.getPreview().getRows()) {
            if (row.getKind() == ProposedEventData.Kind.ACTIVITY) {
                activityIds.add(row.getEventId());
            }
        }
        assertEquals(3, activityIds.size());
        assertEquals(3, new LinkedHashSet<>(activityIds).size());
        assertEquals(3, presenter.getPreview().getActivityCount());
    }

    @Test
    void previewOutputCarriesNoEntities() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));

        interactorFor(trips).preview(simpleInput());

        for (ProposedEventData row : presenter.getPreview().getRows()) {
            assertTrue(row.getActivityId() instanceof String);
            assertTrue(row.getTitle() instanceof String);
        }
        // The DTO exposes identifiers and times only; there is no accessor returning a
        // Trip, Activity or ScheduledEvent anywhere on the preview output.
        for (java.lang.reflect.Method method
                : AutoSchedulePreviewOutputData.class.getDeclaredMethods()) {
            String returned = method.getReturnType().getName();
            assertFalse(returned.startsWith("closeai.domain.entities"),
                    method.getName() + " leaks an entity across the output boundary");
        }
    }

    @Test
    void generatesTravelBlocksBetweenActivities() {
        estimator.defaultMinutes(20);
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(activityEvent("a", 9, 60), activityEvent("b", 12, 60)));

        interactorFor(trips).preview(simpleInput());

        long travelRows = presenter.getPreview().getRows().stream()
                .filter(row -> row.getKind() == ProposedEventData.Kind.TRAVEL).count();
        assertEquals(1, travelRows, "two activities need one journey between them");
        assertEquals(20, presenter.getPreview().getTravelAfterMinutes());
    }

    @Test
    void reportsAnEmptyDayPlanAsAFailureNotAConflict() {
        Trip empty = new Trip("trip-1", "Toronto", ProblemFixtures.TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);

        interactorFor(new FakeTripRepository(empty)).preview(simpleInput());

        assertNull(presenter.getPreview());
        assertTrue(presenter.getFailure().contains("Add activities"));
    }

    @Test
    void reportsAnUnknownTrip() {
        interactorFor(new FakeTripRepository(tripWith(activityEvent("a", 9, 60))))
                .preview(input("other-trip", Collections.emptySet(), Collections.emptyList(),
                        true));

        assertEquals("Trip not found", presenter.getFailure());
    }

    @Test
    void refusesAvailabilityWiderThanTheTripsOwnHours() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));
        AutoScheduleInputData widened = new AutoScheduleInputData("trip-1",
                LocalTime.of(6, 0), LocalTime.of(23, 0), TransportationMode.WALKING,
                Collections.emptySet(), Collections.emptyList(), true);

        interactorFor(trips).preview(widened);

        assertNull(presenter.getPreview());
        assertTrue(presenter.getFailure().contains("within the trip's hours"),
                "a wider window would produce a schedule the Trip could not store");
    }

    @Test
    void acceptsAvailabilityThatNarrowsTheDay() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));
        AutoScheduleInputData narrowed = new AutoScheduleInputData("trip-1",
                LocalTime.of(10, 0), LocalTime.of(16, 0), TransportationMode.WALKING,
                Collections.emptySet(), Collections.emptyList(), true);

        interactorFor(trips).preview(narrowed);

        assertNotNull(presenter.getPreview());
        ProposedEventData row = presenter.getPreview().getRows().get(0);
        assertFalse(row.getStart().isBefore(LocalTime.of(10, 0)));
    }

    @Test
    void reportsAnInvertedAvailabilityWindow() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));
        AutoScheduleInputData inverted = new AutoScheduleInputData("trip-1",
                LocalTime.of(16, 0), LocalTime.of(10, 0), TransportationMode.WALKING,
                Collections.emptySet(), Collections.emptyList(), true);

        interactorFor(trips).preview(inverted);

        assertTrue(presenter.getFailure().contains("later than"));
    }

    @Test
    void reportsALockThatIsNoLongerInThePlan() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));

        interactorFor(trips).preview(input("trip-1",
                new LinkedHashSet<>(Arrays.asList("deleted-event")), Collections.emptyList(),
                true));

        assertNotNull(presenter.getConflict());
        assertEquals(ScheduleConflict.Kind.LOCK_NOT_IN_PLAN, presenter.getConflict().getKind());
        assertEquals("deleted-event", presenter.getConflict().getBlockingEventId());
    }

    @Test
    void keepsALockedActivityAtItsOriginalTime() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(activityEvent("a", 9, 60), activityEvent("dinner", 18, 60)));

        interactorFor(trips).preview(input("trip-1",
                new LinkedHashSet<>(Arrays.asList("dinner")), Collections.emptyList(),
                true));

        ProposedEventData dinner = rowFor(presenter.getPreview(), "dinner");
        assertEquals(LocalTime.of(18, 0), dinner.getStart());
        assertTrue(dinner.isLocked());
    }

    @Test
    void travelFailureStopsBeforeSearchingAndSavesNothing() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(activityEvent("a", 9, 60), activityEvent("b", 12, 60)));
        TravelTimeEstimator failing = new TravelTimeEstimator() {
            @Override
            public TravelEstimate estimate(closeai.domain.valueobjects.Location from,
                                           closeai.domain.valueobjects.Location to,
                                           TransportationMode mode,
                                           java.time.LocalDateTime departure) {
                throw new IllegalStateException("routing unavailable");
            }

            @Override
            public boolean isTimeSensitive(TransportationMode mode) {
                return false;
            }
        };

        new AutoScheduleInteractor(trips, failing, weather, presenter, REGISTERED,
                new ScheduleEngine()).preview(simpleInput());

        assertNull(presenter.getPreview());
        assertTrue(presenter.getFailure().contains("Travel times are unavailable"));
        assertEquals(0, trips.getSaveCount());
    }

    @Test
    void anUnavailableForecastStillProducesAScheduleWithAWarning() {
        weather.returning(WeatherContext.unavailable());
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));

        interactorFor(trips).preview(input("trip-1", Collections.emptySet(),
                Collections.emptyList(), true));

        assertNotNull(presenter.getPreview(), "weather must never cost the user their schedule");
        assertTrue(presenter.getPreview().getWarnings().stream()
                .anyMatch(warning -> warning.contains("Weather could not be considered")));
    }

    @Test
    void aWeatherGatewayThatThrowsIsStillSurvivable() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));

        new AutoScheduleInteractor(trips, estimator, new FakeWeatherContextGateway().thatFails(),
                presenter, REGISTERED, new ScheduleEngine())
                .preview(input("trip-1", Collections.emptySet(), Collections.emptyList(),
                        true));

        assertNotNull(presenter.getPreview());
        assertTrue(presenter.getPreview().getWarnings().stream()
                .anyMatch(warning -> warning.contains("Weather could not be considered")));
    }

    @Test
    void aWholeDayForecastSaysSoRatherThanImplyingItShapedTheTiming() {
        weather.returning(WeatherContext.tripLevel(WeatherSeverity.LOW));
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));

        interactorFor(trips).preview(input("trip-1", Collections.emptySet(),
                Collections.emptyList(), true));

        assertTrue(presenter.getPreview().getWarnings().stream()
                .anyMatch(warning -> warning.contains("covers the whole day")),
                "a trip-wide forecast cannot influence timing, and the Preview should admit it");
    }

    @Test
    void anHourlyForecastNeedsNoCaveat() {
        java.util.Map<Integer, WeatherSeverity> byHour = new java.util.HashMap<>();
        for (int hour = 9; hour < 21; hour++) {
            byHour.put(hour, WeatherSeverity.LOW);
        }
        weather.returning(WeatherContext.hourly(byHour));
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));

        interactorFor(trips).preview(input("trip-1", Collections.emptySet(),
                Collections.emptyList(), true));

        assertTrue(presenter.getPreview().getWarnings().isEmpty());
    }

    @Test
    void reportsTheBuiltInObjectivesAndWhetherOrderWasKept() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));

        interactorFor(trips).preview(input("trip-1", Collections.emptySet(),
                Collections.emptyList(), true));

        assertEquals(Arrays.asList(PolicyId.WEATHER, PolicyId.MEAL_TIME, PolicyId.DAYLIGHT,
                        PolicyId.REDUCE_IDLE, PolicyId.PRESERVE_ORDER),
                presenter.getPreview().getActivePolicies());
        assertTrue(presenter.getPreview().isKeptCurrentOrder());
    }

    @Test
    void turningOffKeepMyOrderIsReflectedInTheOutput() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));

        interactorFor(trips).preview(input("trip-1", Collections.emptySet(),
                Collections.emptyList(), false));

        assertFalse(presenter.getPreview().isKeptCurrentOrder());
        assertFalse(presenter.getPreview().getActivePolicies().contains(PolicyId.PRESERVE_ORDER));
    }

    @Test
    void discloseTravelQualityHonestlyWhenTheProviderCannotSay() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(activityEvent("a", 9, 60), activityEvent("b", 12, 60)));

        interactorFor(trips).preview(simpleInput());

        assertEquals(TravelEstimateQuality.ROUTED, presenter.getPreview().getTravelQuality(),
                "the fake reports routed answers; the real wrapper reports UNKNOWN");
    }

    @Test
    void applySavesExactlyThePreviewedSchedule() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(activityEvent("a", 9, 60), activityEvent("b", 12, 60)));
        AutoScheduleInteractor interactor = interactorFor(trips);
        interactor.preview(simpleInput());
        AutoSchedulePreviewOutputData preview = presenter.getPreview();

        interactor.apply(new AutoScheduleApplyInputData("trip-1",
                preview.getScheduleFingerprint(), preview.getRows()));

        assertNotNull(presenter.getApplied());
        assertEquals(1, trips.getSaveCount());
        List<ScheduledEvent> saved = trips.current().getScheduledEvents();
        assertEquals(preview.getRows().size(), saved.size());
        for (int i = 0; i < saved.size(); i++) {
            assertEquals(preview.getRows().get(i).getStart(), saved.get(i).getStartTime());
            assertEquals(preview.getRows().get(i).getEnd(), saved.get(i).getEndTime());
        }
    }

    @Test
    void applyRejectsAPreviewMadeBeforeThePlanChanged() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(activityEvent("a", 9, 60), activityEvent("b", 12, 60)));
        AutoScheduleInteractor interactor = interactorFor(trips);
        interactor.preview(simpleInput());
        AutoSchedulePreviewOutputData stale = presenter.getPreview();

        // The user edits the Day Plan after previewing.
        trips.save(tripWith(activityEvent("a", 10, 60), activityEvent("b", 13, 60)));
        int savesBefore = trips.getSaveCount();

        interactor.apply(new AutoScheduleApplyInputData("trip-1",
                stale.getScheduleFingerprint(), stale.getRows()));

        assertTrue(presenter.getFailure().contains("changed after this Preview"));
        assertEquals(savesBefore, trips.getSaveCount(), "a stale preview must not be saved");
    }

    @Test
    void applyWithNothingToSaveIsRejected() {
        FakeTripRepository trips = new FakeTripRepository(tripWith(activityEvent("a", 9, 60)));

        interactorFor(trips).apply(new AutoScheduleApplyInputData("trip-1", "",
                Collections.emptyList()));

        assertEquals("Nothing to apply", presenter.getFailure());
        assertEquals(0, trips.getSaveCount());
    }

    @Test
    void cancellingSimplyMeansNeverCallingApply() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(activityEvent("a", 9, 60), activityEvent("b", 12, 60)));

        interactorFor(trips).preview(simpleInput());

        assertEquals(0, trips.getSaveCount());
        assertEquals(LocalTime.of(9, 0),
                trips.current().getScheduledEvents().get(0).getStartTime());
    }

    @Test
    void refusesMoreActivitiesThanOneDayCanSensiblyHold() {
        List<ScheduledEvent> many = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Activity activity = ProblemFixtures.activity("a" + i,
                    LocalTime.of(9, 0), LocalTime.of(21, 0));
            LocalTime start = LocalTime.of(9, 0).plusMinutes(i * 45L);
            many.add(new ScheduledEvent("a" + i, activity, start, start.plusMinutes(30),
                    EventType.ACTIVITY, ""));
        }
        Trip trip = new Trip("trip-1", "Toronto", ProblemFixtures.TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(23, 0), TransportationMode.WALKING);
        trip.replaceSchedule(many);

        interactorFor(new FakeTripRepository(trip)).preview(simpleInput());

        assertTrue(presenter.getFailure().contains("up to 15 activities"));
    }

    private static ProposedEventData rowFor(AutoSchedulePreviewOutputData preview, String eventId) {
        return preview.getRows().stream()
                .filter(row -> row.getEventId().equals(eventId))
                .findFirst().orElseThrow(AssertionError::new);
    }
}
