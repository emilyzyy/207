package closeai.application.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import closeai.domain.valueobjects.OpeningHours;
import closeai.domain.valueobjects.TransportationMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the traveller is told about opening hours, which is the whole point of keeping
 * "unknown" and "closed" apart.
 *
 * <p>Treating a venue with no recorded hours as unconstrained is the only workable default —
 * most places in OpenStreetMap have no {@code opening_hours} tag, and refusing to plan around
 * them would make Autoschedule useless. But that permissiveness is a guess, so the run says
 * where it guessed. These tests exist because a silent guess and a stated one look identical
 * in a screenshot and are not at all the same promise.</p>
 */
class OpeningHoursWarningTest {

    private static final List<SoftPolicy> REGISTERED = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private final RecordingPresenter presenter = new RecordingPresenter();
    private final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator();
    private final FakeWeatherContextGateway weather = new FakeWeatherContextGateway();

    private static ScheduledEvent event(String id, int startHour, OpeningHours hours) {
        Activity activity = ProblemFixtures.activityWithHours(id, hours);
        LocalTime start = LocalTime.of(startHour, 0);
        return new ScheduledEvent(id, activity, start, start.plusMinutes(60),
                EventType.ACTIVITY, "");
    }

    private FakeTripRepository tripWith(ScheduledEvent... events) {
        Trip trip = new Trip("trip-1", "Toronto", ProblemFixtures.TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(events));
        return new FakeTripRepository(trip);
    }

    private void run(FakeTripRepository trips) {
        new AutoScheduleInteractor(trips, estimator, weather, presenter, REGISTERED,
                new ScheduleEngine())
                .preview(new AutoScheduleInputData("trip-1", LocalTime.of(9, 0),
                        LocalTime.of(21, 0), TransportationMode.WALKING,
                        Collections.emptySet(), Collections.emptyList(), true, false));
    }

    @Test
    void aVenueWithNoRecordedHoursIsScheduledWithoutComment() {
        run(tripWith(event("Casa Loma", 10, OpeningHours.unknown())));

        assertNotNull(presenter.getPreview(), "unknown hours must not prevent a schedule");
        assertTrue(presenter.getPreview().getWarnings().isEmpty(),
                "no published hours is the ordinary case, not something to caution about: "
                        + presenter.getPreview().getWarnings());
    }

    @Test
    void aVenueWithRealHoursIsNotWarnedAboutEither() {
        run(tripWith(event("Gallery", 10, ProblemFixtures.hoursOn(DayOfWeek.WEDNESDAY,
                "09:00-18:00"))));

        assertNotNull(presenter.getPreview());
        assertTrue(presenter.getPreview().getWarnings().isEmpty(),
                presenter.getPreview().getWarnings().toString());
    }

    @Test
    void awholeDayOfVenuesWithoutHoursProducesNoWarningsAtAll() {
        run(tripWith(event("A", 9, OpeningHours.unknown()),
                event("B", 11, OpeningHours.unknown()),
                event("C", 13, OpeningHours.unknown()),
                event("D", 15, OpeningHours.unknown())));

        assertNotNull(presenter.getPreview());
        assertTrue(presenter.getPreview().getWarnings().isEmpty(),
                "four unknowns is a normal day, not four problems: "
                        + presenter.getPreview().getWarnings());
    }

    /**
     * The permissiveness itself is unchanged — it is only the commentary that went. An
     * activity with no published hours is still placed inside its general daily window
     * rather than refused, and that is what actually matters.
     */
    @Test
    void aVenueWithNoRecordedHoursIsStillActuallyScheduled() {
        run(tripWith(event("Casa Loma", 10, OpeningHours.unknown())));

        assertEquals(1, presenter.getPreview().getRows().size());
        assertEquals("Casa Loma", presenter.getPreview().getRows().get(0).getTitle());
    }

    /**
     * A venue on record as shut cannot be scheduled, and the engine cannot produce a partial
     * day, so this is a conflict rather than a preview. The conflict names the venue.
     */
    @Test
    void aVenueClosedOnTheTripDateBecomesANamedConflict() {
        run(tripWith(event("Saturday Market", 10,
                ProblemFixtures.hoursOn(DayOfWeek.SATURDAY, "08:00-14:00"))));

        assertNull(presenter.getPreview(),
                "a venue that is shut cannot quietly be scheduled anyway");
        AutoScheduleConflictOutputData conflict = presenter.getConflict();
        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.ACTIVITY_CANNOT_FIT, conflict.getKind());
        assertEquals("Saturday Market", conflict.getSubject());
        assertEquals(0, conflict.getAvailableMinutes(),
                "shut all day is nought minutes, not a short day");
    }

    private static void assertNull(Object value, String message) {
        org.junit.jupiter.api.Assertions.assertNull(value, message);
    }
}
