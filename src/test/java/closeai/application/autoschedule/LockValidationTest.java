package closeai.application.autoschedule;

import static closeai.application.autoschedule.ProblemFixtures.at;
import static closeai.application.autoschedule.ProblemFixtures.flatMatrix;
import static closeai.application.autoschedule.ProblemFixtures.lockedTask;
import static closeai.application.autoschedule.ProblemFixtures.noBlockedWindows;
import static closeai.application.autoschedule.ProblemFixtures.task;
import static closeai.application.autoschedule.ProblemFixtures.tasks;
import static closeai.application.autoschedule.ProblemFixtures.window;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.application.autoschedule.engine.ScheduleEngine;
import closeai.application.autoschedule.engine.ScheduleSearchResult;
import closeai.application.autoschedule.engine.SearchBudget;
import java.util.List;
import org.junit.jupiter.api.Test;

class LockValidationTest {

    private final ProblemValidator validator = new ProblemValidator();
    private final ScheduleEngine engine = new ScheduleEngine();

    @Test
    void aLockOutsideTheAvailabilityWindowIsRejected() {
        List<ScheduleTask> items = tasks(
                lockedTask("late", 60, 0, at(9, 0), at(23, 0), at(22, 0)));

        ScheduleConflict conflict = validator.validate(window(9, 21), items, noBlockedWindows());

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_OUTSIDE_AVAILABILITY, conflict.getKind());
        assertEquals("late", conflict.getBlockingEventId());
    }

    @Test
    void aLockOutsideTheVenuesOpeningHoursIsRejected() {
        // Locked at 09:30 but the venue does not open until 11:00.
        List<ScheduleTask> items = tasks(
                lockedTask("museum", 60, 0, at(11, 0), at(17, 0), at(9, 30)));

        ScheduleConflict conflict = validator.validate(window(9, 21), items, noBlockedWindows());

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_OUTSIDE_OPENING_HOURS, conflict.getKind());
        assertEquals("museum", conflict.getBlockingEventId());
    }

    @Test
    void twoLocksAtOverlappingTimesAreRejected() {
        List<ScheduleTask> items = tasks(
                lockedTask("lunch", 60, 0, at(9, 0), at(22, 0), at(12, 0)),
                lockedTask("tour", 60, 1, at(9, 0), at(22, 0), at(12, 30)));

        ScheduleConflict conflict = validator.validate(window(9, 21), items, noBlockedWindows());

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCKS_OVERLAP, conflict.getKind());
    }

    @Test
    void backToBackLocksAreAllowed() {
        List<ScheduleTask> items = tasks(
                lockedTask("first", 60, 0, at(9, 0), at(22, 0), at(12, 0)),
                lockedTask("second", 60, 1, at(9, 0), at(22, 0), at(13, 0)));

        assertNull(validator.validate(window(9, 21), items, noBlockedWindows()),
                "an activity ending at 13:00 does not overlap one starting at 13:00");
    }

    @Test
    void lockedActivitiesKeepTheirExactTimes() {
        List<ScheduleTask> items = tasks(
                task("museum", 60, 0, at(9, 0), at(21, 0)),
                lockedTask("dinner", 90, 1, at(9, 0), at(22, 0), at(18, 30)),
                task("park", 60, 2, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 15));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound());
        PlacedActivity dinner = placementOf(result, "dinner");
        assertEquals(at(18, 30), dinner.getStart());
        assertEquals(at(20, 0), dinner.getEnd());
    }

    @Test
    void travelOutOfALockedActivityIsAccountedFor() {
        List<ScheduleTask> items = tasks(
                lockedTask("dinner", 60, 0, at(9, 0), at(22, 0), at(12, 0)),
                task("after", 60, 1, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 30));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound());
        PlacedActivity after = placementOf(result, "after");
        assertTrue(after.getStart().equals(at(13, 30)) || after.getStart().isBefore(at(12, 0)),
                "an activity after dinner must allow 30 minutes of travel, was " + after.getStart());
    }

    @Test
    void travelIntoALockedActivityThatCannotFitIsAConflict() {
        List<ScheduleTask> items = tasks(
                task("far", 120, 0, at(9, 0), at(11, 0)),
                lockedTask("locked", 60, 1, at(9, 0), at(22, 0), at(11, 10)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 45));

        assertFalse(engine.search(problem, SearchBudget.defaultBudget()).isFound());
    }

    @Test
    void aLockedActivityIsNeverMovedToImproveTheScore() {
        // Placing dinner at its lock costs travel; the search must not "fix" that.
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                lockedTask("dinner", 60, 1, at(9, 0), at(22, 0), at(15, 0)),
                task("b", 60, 2, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 10));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertEquals(at(15, 0), placementOf(result, "dinner").getStart());
    }

    private PlacedActivity placementOf(ScheduleSearchResult result, String eventId) {
        return result.getPlan().getPlacements().stream()
                .filter(placed -> placed.getTask().getEventId().equals(eventId))
                .findFirst().orElseThrow(AssertionError::new);
    }

    @Test
    void aLockAtAVenueThatIsShutAllDayIsRejectedByName() {
        // Real hours, and they say Saturday. The trip is a Wednesday.
        List<ScheduleTask> items = tasks(ProblemFixtures.lockedTaskWithHours("saturdaysOnly",
                60, 0, ProblemFixtures.hoursOn(java.time.DayOfWeek.SATURDAY, "10:00-16:00"),
                at(12, 0)));

        ScheduleConflict conflict = validator.validate(window(9, 21), items, noBlockedWindows());

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_OUTSIDE_OPENING_HOURS, conflict.getKind());
        assertEquals("saturdaysOnly", conflict.getBlockingEventId());
        assertEquals("saturdaysOnly", conflict.getSubject(),
                "the traveller must be told which pinned activity is the problem");
    }

    @Test
    void aLockSpanningAVenuesMiddayClosureIsOutsideItsOpeningHours() {
        // Open 09:00-12:00 and 14:00-18:00; pinned 11:30-12:30, which straddles the closure.
        // Reading the day as "open 09:00 to 18:00" would wrongly allow this.
        List<ScheduleTask> items = tasks(ProblemFixtures.lockedTaskWithHours("siesta", 60, 0,
                ProblemFixtures.hoursOn(java.time.DayOfWeek.WEDNESDAY,
                        "09:00-12:00", "14:00-18:00"),
                at(11, 30)));

        ScheduleConflict conflict = validator.validate(window(9, 21), items, noBlockedWindows());

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_OUTSIDE_OPENING_HOURS, conflict.getKind());
    }

    @Test
    void aLockInsideOneOfSeveralOpeningWindowsIsAccepted() {
        List<ScheduleTask> items = tasks(ProblemFixtures.lockedTaskWithHours("afternoon", 60, 0,
                ProblemFixtures.hoursOn(java.time.DayOfWeek.WEDNESDAY,
                        "09:00-12:00", "14:00-18:00"),
                at(15, 0)));

        assertNull(validator.validate(window(9, 21), items, noBlockedWindows()),
                "a pin in the afternoon shift is perfectly lawful");
    }

    @Test
    void aLockAtAVenueWithUnknownHoursIsNotRejected() {
        List<ScheduleTask> items = tasks(new ScheduleTask("mystery",
                ProblemFixtures.activityWithHours("mystery",
                        closeai.domain.valueobjects.OpeningHours.unknown()),
                60, 0, new TimeWindow(at(12, 0), at(13, 0)), ProblemFixtures.TRIP_DATE));

        assertNull(validator.validate(window(9, 21), items, noBlockedWindows()),
                "no provider data must not turn a pin into a conflict");
    }
}
