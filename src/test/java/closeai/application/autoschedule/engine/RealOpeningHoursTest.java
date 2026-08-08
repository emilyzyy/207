package closeai.application.autoschedule.engine;

import static closeai.application.autoschedule.ProblemFixtures.at;
import static closeai.application.autoschedule.ProblemFixtures.flatMatrix;
import static closeai.application.autoschedule.ProblemFixtures.hoursOn;
import static closeai.application.autoschedule.ProblemFixtures.noBlockedWindows;
import static closeai.application.autoschedule.ProblemFixtures.task;
import static closeai.application.autoschedule.ProblemFixtures.taskWithHours;
import static closeai.application.autoschedule.ProblemFixtures.tasks;
import static closeai.application.autoschedule.ProblemFixtures.window;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.ProblemFixtures;
import closeai.application.autoschedule.ScheduleConflict;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.ScheduleTask;
import closeai.application.autoschedule.TimeWindow;
import closeai.domain.valueobjects.OpeningHours;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The scheduler against real, imported opening hours.
 *
 * <p>Opening hours are a hard constraint here, in the same class as the traveller's own
 * unavailable periods: a day that would put a visit behind a locked door is not a worse
 * schedule, it is not a schedule. What the search may do freely is <em>travel</em> outside
 * those hours, because walking to a museum before it opens is how anyone gets there.</p>
 *
 * <p>{@link ProblemFixtures#TRIP_DATE} is a Wednesday, so hours are declared on Wednesday
 * unless a test is specifically about the wrong weekday.</p>
 */
class RealOpeningHoursTest {

    private final ScheduleEngine engine = new ScheduleEngine();

    private ScheduleSearchResult search(List<ScheduleTask> items, TimeWindow availability) {
        return engine.search(new ScheduleProblem(availability, items, noBlockedWindows(),
                flatMatrix(items, availability, 10)), SearchBudget.defaultBudget());
    }

    private static PlacedActivity placementOf(ScheduleSearchResult result, String id) {
        for (PlacedActivity placed : result.getPlan().getPlacements()) {
            if (placed.getTask().getEventId().equals(id)) {
                return placed;
            }
        }
        return null;
    }

    // --- inside the hours a provider actually gave us ----------------------------------

    @Test
    void anActivitySitsEntirelyInsideAnImportedOpeningInterval() {
        List<ScheduleTask> items = tasks(
                taskWithHours("gallery", 60, 0, hoursOn(DayOfWeek.WEDNESDAY, "10:00-16:00")),
                task("other", 60, 1, at(9, 0), at(21, 0)));

        PlacedActivity gallery = placementOf(search(items, window(9, 21)), "gallery");

        assertNotNull(gallery);
        assertFalse(gallery.getStart().isBefore(at(10, 0)));
        assertFalse(gallery.getEnd().isAfter(at(16, 0)));
    }

    @Test
    void anActivityIsMovedOutOfAnHourTheVenueIsShut() {
        // Left alone it would start at 9:00 with the day; the venue does not open until 14:00.
        List<ScheduleTask> items = tasks(
                taskWithHours("evening", 60, 0, hoursOn(DayOfWeek.WEDNESDAY, "14:00-20:00")));

        PlacedActivity placed = placementOf(search(items, window(9, 21)), "evening");

        assertNotNull(placed);
        assertEquals(at(14, 0), placed.getStart(),
                "the earliest lawful start is the moment the doors open");
    }

    @Test
    void travelMayHappenWhileTheVenueIsStillShut() {
        // Ten minutes of walking, and the venue opens at 14:00: departing at 13:50 is fine.
        List<ScheduleTask> items = tasks(
                task("first", 60, 0, at(9, 0), at(21, 0)),
                taskWithHours("second", 60, 1, hoursOn(DayOfWeek.WEDNESDAY, "14:00-20:00")));

        PlacedActivity second = placementOf(search(items, window(9, 21)), "second");

        assertNotNull(second);
        assertEquals(at(14, 0), second.getStart());
        assertTrue(second.getTravelDeparture().isBefore(at(14, 0)),
                "the journey is allowed to run before the doors open: " + second.getTravelDeparture());
    }

    // --- the exact edges ---------------------------------------------------------------

    @Test
    void aVisitMayStartOnTheOpeningMinuteAndEndOnTheClosingMinute() {
        List<ScheduleTask> items = tasks(
                taskWithHours("exact", 120, 0, hoursOn(DayOfWeek.WEDNESDAY, "13:00-15:00")));

        PlacedActivity placed = placementOf(search(items, window(9, 21)), "exact");

        assertNotNull(placed, "a visit that exactly fills the opening interval must be allowed");
        assertEquals(at(13, 0), placed.getStart());
        assertEquals(at(15, 0), placed.getEnd());
    }

    @Test
    void aVisitOneMinuteTooLongForTheIntervalIsRefused() {
        List<ScheduleTask> items = tasks(
                taskWithHours("toolong", 121, 0, hoursOn(DayOfWeek.WEDNESDAY, "13:00-15:00")));

        ScheduleSearchResult result = search(items, window(9, 21));

        assertFalse(result.isFound(),
                "overrunning closing time by a minute is still overrunning closing time");
        assertEquals(ScheduleConflict.Kind.ACTIVITY_CANNOT_FIT, result.getConflict().getKind());
        assertEquals("toolong", result.getConflict().getBlockingEventId());
    }

    @Test
    void aDurationThatWouldRunPastClosingIsRefusedRatherThanTruncated() {
        List<ScheduleTask> items = tasks(
                taskWithHours("long", 180, 0, hoursOn(DayOfWeek.WEDNESDAY, "10:00-12:00")));

        ScheduleSearchResult result = search(items, window(9, 21));

        assertFalse(result.isFound(),
                "the schedule must not silently shorten the visit to make it fit");
        assertEquals(ScheduleConflict.Kind.ACTIVITY_CANNOT_FIT, result.getConflict().getKind());
    }

    @Test
    void theDiagnosisMeasuresTheLongestShiftNotTheWholeDaysSpan() {
        // Open 09:00-11:00 and 15:00-17:00: six hours apart, but only two hours usable.
        List<ScheduleTask> items = tasks(taskWithHours("split", 180, 0,
                hoursOn(DayOfWeek.WEDNESDAY, "09:00-11:00", "15:00-17:00")));

        ScheduleSearchResult result = search(items, window(9, 21));

        assertFalse(result.isFound());
        assertEquals(120, result.getConflict().getAvailableMinutes(),
                "the gap between two shifts is not time a visitor can use");
    }

    // --- venues that shut in the middle of the day -------------------------------------

    @Test
    void aVisitMustFitInsideOneIntervalNotAcrossTheGapBetweenTwo() {
        // Open 09:00-12:00 and 14:00-18:00. A ninety-minute visit cannot straddle the closure,
        // and the naive reading -- "open from 09:00 until 18:00" -- would let it start at 11:00.
        List<ScheduleTask> items = tasks(taskWithHours("siesta", 90, 0,
                hoursOn(DayOfWeek.WEDNESDAY, "09:00-12:00", "14:00-18:00")));

        PlacedActivity placed = placementOf(search(items, window(9, 21)), "siesta");

        assertNotNull(placed);
        boolean morning = !placed.getStart().isBefore(at(9, 0)) && !placed.getEnd().isAfter(at(12, 0));
        boolean afternoon = !placed.getStart().isBefore(at(14, 0))
                && !placed.getEnd().isAfter(at(18, 0));
        assertTrue(morning || afternoon,
                "must sit in one shift or the other, but was " + placed.getStart()
                        + "-" + placed.getEnd());
    }

    @Test
    void aVisitTooLongForTheMorningShiftFallsToTheAfternoonOne() {
        List<ScheduleTask> items = tasks(taskWithHours("afternoonOnly", 180, 0,
                hoursOn(DayOfWeek.WEDNESDAY, "09:00-11:00", "14:00-18:00")));

        PlacedActivity placed = placementOf(search(items, window(9, 21)), "afternoonOnly");

        assertNotNull(placed, "three hours does not fit the morning, but does fit the afternoon");
        assertEquals(at(14, 0), placed.getStart());
    }

    // --- closed, and unknown, which are not the same thing -----------------------------

    @Test
    void aVenueClosedOnTheTripDateCannotBeScheduledAtAll() {
        // Hours are known, and they say Saturday only. The trip is a Wednesday.
        List<ScheduleTask> items = tasks(
                taskWithHours("saturdaysOnly", 60, 0, hoursOn(DayOfWeek.SATURDAY, "10:00-16:00")),
                task("open", 60, 1, at(9, 0), at(21, 0)));

        ScheduleSearchResult result = search(items, window(9, 21));

        assertFalse(result.isFound(), "a venue on record as shut must not be scheduled");
        ScheduleConflict conflict = result.getConflict();
        assertEquals(ScheduleConflict.Kind.ACTIVITY_CANNOT_FIT, conflict.getKind());
        assertEquals("saturdaysOnly", conflict.getBlockingEventId(),
                "and the traveller must be told which venue it is");
        assertEquals(0, conflict.getAvailableMinutes());
    }

    @Test
    void unknownHoursConstrainNothingAndAreNotTreatedAsClosed() {
        ScheduleTask unknown = new ScheduleTask("mystery",
                ProblemFixtures.activityWithHours("mystery", OpeningHours.unknown()),
                60, 0, null, ProblemFixtures.TRIP_DATE);
        List<ScheduleTask> items = tasks(unknown);

        PlacedActivity placed = placementOf(search(items, window(9, 21)), "mystery");

        assertNotNull(placed, "no provider data must never mean no schedule");
        assertFalse(unknown.hasKnownHours());
        assertFalse(unknown.isClosedAllDay());
        assertEquals(at(9, 0), placed.getStart(),
                "with nothing known, the day's own availability is the only limit");
    }

    @Test
    void aVenueOpenAroundTheClockIsScheduledAtTheEarliestUsefulTime() {
        List<ScheduleTask> items = tasks(new ScheduleTask("always",
                ProblemFixtures.activityWithHours("always", OpeningHours.alwaysOpen()),
                60, 0, null, ProblemFixtures.TRIP_DATE));

        PlacedActivity placed = placementOf(search(items, window(9, 21)), "always");

        assertNotNull(placed);
        assertEquals(at(9, 0), placed.getStart());
    }

    // --- hours that run past midnight ---------------------------------------------------

    @Test
    void theEveningHalfOfAnOvernightVenueIsUsable() {
        // Normalisation splits "20:00-02:00" at midnight, so the Wednesday side is 20:00-23:59.
        List<ScheduleTask> items = tasks(
                taskWithHours("latebar", 60, 0, hoursOn(DayOfWeek.WEDNESDAY, "20:00-23:59")));

        PlacedActivity placed = placementOf(search(items, window(9, 23)), "latebar");

        assertNotNull(placed);
        assertEquals(at(20, 0), placed.getStart());
    }

    @Test
    void theMorningHalfOfAnOvernightVenueBelongsToTheFollowingDay() {
        // The scheduler plans one day. A bar open Tuesday 20:00-02:00 is open on Wednesday
        // morning until 02:00, and that is what Wednesday's windows must say.
        List<ScheduleTask> items = tasks(
                taskWithHours("earlyhours", 60, 0,
                        hoursOn(DayOfWeek.WEDNESDAY, "00:00-02:00", "20:00-23:59")));

        PlacedActivity placed = placementOf(search(items,
                new TimeWindow(LocalTime.of(0, 0), LocalTime.of(23, 59))), "earlyhours");

        assertNotNull(placed);
        assertEquals(LocalTime.of(0, 0), placed.getStart(),
                "the earliest lawful hour on this day is just after midnight");
    }

    // --- what the task itself reports ---------------------------------------------------

    @Test
    void aTaskReportsTheWindowAVisitIsActuallyInRatherThanTheWholeDaysSpan() {
        ScheduleTask task = taskWithHours("split", 60, 0,
                hoursOn(DayOfWeek.WEDNESDAY, "09:00-12:00", "14:00-18:00"));

        assertEquals(at(9, 0), task.getOpeningTime());
        assertEquals(at(18, 0), task.getClosingTime());
        assertEquals(at(12, 0), task.openingWindowFor(at(10, 0), at(11, 0)).getEnd(),
                "a morning visit is bounded by the morning shift, not by 18:00");
        assertNull(task.openingWindowFor(at(11, 30), at(14, 30)),
                "nothing sits in the gap between the two shifts");
    }

    @Test
    void hoursAreReadForTheTripsWeekdayNotForToday() {
        OpeningHours wednesdaysOnly = hoursOn(DayOfWeek.WEDNESDAY, "10:00-16:00");

        ScheduleTask onWednesday = taskWithHours("w", 60, 0, wednesdaysOnly);
        ScheduleTask onSaturday = new ScheduleTask("s",
                ProblemFixtures.activityWithHours("s", wednesdaysOnly), 60, 0, null,
                ProblemFixtures.TRIP_DATE.plusDays(3));

        assertEquals(at(10, 0), onWednesday.getOpeningTime());
        assertTrue(onSaturday.isClosedAllDay(),
                "the same venue is shut on the Saturday, and the date is what decides");
    }
}
