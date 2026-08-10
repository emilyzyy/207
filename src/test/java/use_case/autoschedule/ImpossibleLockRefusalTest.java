package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static use_case.autoschedule.ProblemFixtures.at;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.OpeningHours;
import entity.valueobjects.TransportationMode;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import use_case.autoschedule.testdoubles.FakeWeatherContextGateway;
import use_case.autoschedule.testdoubles.RecordingPresenter;

/**
 * A pin the day cannot honour is refused by name, before any searching happens.
 *
 * <p>The engine trusts that the locks it is handed are possible — it will place one wherever it
 * is pinned. That trust is only safe because {@link ProblemValidator} runs first, and these
 * tests are what make the layering honest rather than merely convenient. They exist because an
 * independent oracle noticed the engine placing a lock inside a venue's lunchtime closure and
 * inside an unavailable period, and the answer to "why is that acceptable?" has to be a test
 * rather than an argument.</p>
 */
class ImpossibleLockRefusalTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    private static OpeningHours hours(String... spans) {
        List<OpeningHours.TimeInterval> intervals = new ArrayList<>();
        for (String span : spans) {
            String[] halves = span.split("-");
            intervals.add(new OpeningHours.TimeInterval(
                    LocalTime.parse(halves[0]), LocalTime.parse(halves[1])));
        }
        Map<DayOfWeek, List<OpeningHours.TimeInterval>> week = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            week.put(day, intervals);
        }
        return OpeningHours.of(week);
    }

    private static Trip tripWith(OpeningHours pinnedHours, LocalTime pinnedStart) {
        Activity pinned = new Activity("pin", "Split Hours Bistro", ActivityCategory.FOOD,
                new Location(43.65, -79.38, "pin"), 4.5, 60, at(9, 0), at(20, 0),
                IndoorOutdoorType.INDOOR, "none", "hours", pinnedHours);
        Activity other = new Activity("other", "Museum", ActivityCategory.MUSEUM,
                new Location(43.66, -79.39, "other"), 4.5, 60, at(8, 0), at(20, 0),
                IndoorOutdoorType.INDOOR, "none", "hours", hours("08:00-20:00"));

        Trip trip = new Trip("trip-1", "Toronto", DATE, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(
                new ScheduledEvent("pin", pinned, pinnedStart, pinnedStart.plusMinutes(60),
                        EventType.ACTIVITY, ""),
                new ScheduledEvent("other", other, at(16, 0), at(17, 0),
                        EventType.ACTIVITY, "")));
        return trip;
    }

    private static RecordingPresenter run(Trip trip, List<TimeWindow> unavailable) {
        RecordingPresenter presenter = new RecordingPresenter();
        new AutoScheduleInteractor(new FakeTripRepository(trip),
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(10),
                new FakeWeatherContextGateway(), presenter,
                Collections.emptyList(), new ScheduleEngine())
                .preview(new AutoScheduleInputData("trip-1", at(9, 0), at(21, 0),
                        TransportationMode.WALKING, Collections.singleton("pin"),
                        unavailable, false, true));
        return presenter;
    }

    /** Pinned into the gap between a venue's two opening windows. */
    @Test
    void aPinInsideAVenuesMiddayClosureIsRefusedByName() {
        RecordingPresenter presenter = run(
                tripWith(hours("09:00-12:00", "15:00-20:00"), at(12, 30)),
                Collections.emptyList());

        assertNull(presenter.getPreview(),
                "a pin the venue is shut for cannot quietly be scheduled anyway");
        AutoScheduleConflictOutputData conflict = presenter.getConflict();
        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_OUTSIDE_OPENING_HOURS, conflict.getKind(),
                "starting and ending while open is not the same as being open throughout");
        assertEquals("Split Hours Bistro", conflict.getSubject(),
                "and the traveller must be told which pin");
    }

    /** Pinned to a time the traveller already said they were busy. */
    @Test
    void aPinInsideAnUnavailablePeriodIsRefusedByName() {
        RecordingPresenter presenter = run(
                tripWith(hours("08:00-20:00"), at(13, 0)),
                Collections.singletonList(new TimeWindow(at(13, 0), at(14, 0))));

        assertNull(presenter.getPreview());
        AutoScheduleConflictOutputData conflict = presenter.getConflict();
        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_INSIDE_UNAVAILABLE_PERIOD, conflict.getKind());
        assertEquals("Split Hours Bistro", conflict.getSubject());
    }

    /** A pin that is genuinely possible still works, so the guard is not simply refusing. */
    @Test
    void aPinTheDayCanHonourStillProducesASchedule() {
        RecordingPresenter presenter = run(
                tripWith(hours("09:00-12:00", "15:00-20:00"), at(10, 0)),
                Collections.emptyList());

        assertNull(presenter.getConflict(),
                presenter.getConflict() == null ? "" : presenter.getConflict().getKind().name());
        assertNotNull(presenter.getPreview(), "this pin sits inside an opening window");
        boolean pinnedWhereAsked = false;
        for (ProposedEventData row : presenter.getPreview().getRows()) {
            if (row.getEventId().equals("pin")) {
                pinnedWhereAsked = row.getStart().equals(at(10, 0));
            }
        }
        assertTrue(pinnedWhereAsked, "and it must be honoured exactly");
    }

    /** Nothing is saved either way: a refusal leaves the day untouched. */
    @Test
    void aRefusedPinLeavesTheSavedDayAlone() {
        Trip trip = tripWith(hours("09:00-12:00", "15:00-20:00"), at(12, 30));
        FakeTripRepository trips = new FakeTripRepository(trip);
        RecordingPresenter presenter = new RecordingPresenter();
        new AutoScheduleInteractor(trips,
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(10),
                new FakeWeatherContextGateway(), presenter,
                Collections.emptyList(), new ScheduleEngine())
                .preview(new AutoScheduleInputData("trip-1", at(9, 0), at(21, 0),
                        TransportationMode.WALKING, Collections.singleton("pin"),
                        Collections.emptyList(), false, true));

        assertEquals(2, trips.findById("trip-1").get().getScheduledEvents().size(),
                "a refusal is not a mutation");
        assertEquals(at(12, 30), trips.findById("trip-1").get()
                .getScheduledEvents().get(0).getStartTime());
    }
}
