package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static use_case.autoschedule.ProblemFixtures.at;
import static use_case.autoschedule.ProblemFixtures.flatMatrix;
import static use_case.autoschedule.ProblemFixtures.lockedTask;
import static use_case.autoschedule.ProblemFixtures.task;
import static use_case.autoschedule.ProblemFixtures.tasks;
import static use_case.autoschedule.ProblemFixtures.window;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.engine.ScheduleSearchResult;
import use_case.autoschedule.engine.SearchBudget;

/**
 * Unavailable windows are inviolable: the user has said they are unavailable for any
 * itinerary event, so neither an activity nor a journey may run through one.
 */
class UnavailableWindowTest {

    private final ScheduleEngine engine = new ScheduleEngine();
    private final ProblemValidator validator = new ProblemValidator();

    @Test
    void noActivityOverlapsAnUnavailableWindow() {
        final List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        final TimeWindow blocked = new TimeWindow(at(10, 0), at(12, 0));
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(blocked), flatMatrix(items, window(9, 21), 5));

        final ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound());
        for (PlacedActivity placed : result.getPlan().getPlacements()) {
            assertFalse(placed.window().overlaps(blocked), placed + " overlaps a blocked period");
        }
    }

    @Test
    void noTravelBlockRunsThroughAnUnavailableWindow() {
        // Leaving "a" at 10:00 would travel 10:00-10:40, straight through the block.
        final List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        final TimeWindow blocked = new TimeWindow(at(10, 0), at(12, 0));
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(blocked), flatMatrix(items, window(9, 21), 40));

        final ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound());
        final PlacedActivity second = result.getPlan().getPlacements().get(1);
        assertNotNull(second.travelWindow());
        assertFalse(second.travelWindow().overlaps(blocked),
                "travel " + second.travelWindow() + " runs through the blocked period");
    }

    @Test
    void theTravellerWaitsForTheWindowToEndBeforeSettingOut() {
        final List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        final TimeWindow blocked = new TimeWindow(at(10, 0), at(12, 0));
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(blocked), flatMatrix(items, window(9, 21), 40));

        final ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());
        final PlacedActivity second = result.getPlan().getPlacements().get(1);

        assertEquals(at(12, 0), second.getTravelDeparture(),
                "the journey should start when the blocked period ends");
        assertEquals(at(12, 40), second.getStart());
    }

    @Test
    void waitingOutABlockedPeriodIsNotCountedAsWastedTime() {
        final List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(new TimeWindow(at(10, 0), at(12, 0))),
                flatMatrix(items, window(9, 21), 5));

        final ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());
        final PlacedActivity second = result.getPlan().getPlacements().get(1);

        assertTrue(second.getIdleMinutesBefore() > 0);
        assertEquals(0, second.getAvoidableIdleMinutes(),
                "time the user declared unavailable is not idle the schedule could reclaim");
    }

    @Test
    void aDayWithNoRoomLeftAroundTheBlockIsAConflict() {
        final List<ScheduleTask> items = tasks(
                task("a", 120, 0, at(9, 0), at(21, 0)),
                task("b", 120, 1, at(9, 0), at(21, 0)));
        // 09:00-21:00 with a 10-hour block leaves two hours for two two-hour activities.
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(new TimeWindow(at(11, 0), at(21, 0))),
                flatMatrix(items, window(9, 21), 10));

        final ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertFalse(result.isFound());
    }

    @Test
    void overlappingUnavailableWindowsAreRejectedRatherThanMerged() {
        final List<ScheduleTask> items = tasks(task("a", 60, 0, at(9, 0), at(21, 0)));
        final List<TimeWindow> overlapping = Arrays.asList(
                new TimeWindow(at(12, 0), at(13, 0)),
                new TimeWindow(at(12, 30), at(14, 0)));

        final ScheduleConflict conflict = validator.validate(window(9, 21), items, overlapping);

        assertNotNull(conflict, "overlapping windows are a typo worth surfacing, not merging");
        assertEquals(ScheduleConflict.Kind.LOCKS_OVERLAP, conflict.getKind());
    }

    @Test
    void anUnavailableWindowOutsideAvailabilityIsRejected() {
        final List<ScheduleTask> items = tasks(task("a", 60, 0, at(9, 0), at(21, 0)));

        final ScheduleConflict conflict = validator.validate(window(9, 17), items,
                Arrays.asList(new TimeWindow(at(18, 0), at(19, 0))));

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_OUTSIDE_AVAILABILITY, conflict.getKind());
    }

    @Test
    void aLockedActivityInsideAnUnavailableWindowIsAConflict() {
        final List<ScheduleTask> items = tasks(
                lockedTask("dinner", 60, 0, at(9, 0), at(22, 0), at(12, 30)));

        final ScheduleConflict conflict = validator.validate(window(9, 21), items,
                Arrays.asList(new TimeWindow(at(12, 0), at(13, 0))));

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_INSIDE_UNAVAILABLE_PERIOD, conflict.getKind());
        assertEquals("dinner", conflict.getBlockingEventId());
    }

    @Test
    void aValidRequestPassesValidation() {
        final List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                lockedTask("dinner", 90, 1, at(9, 0), at(22, 0), at(18, 30)));

        assertNull(validator.validate(window(9, 21), items,
                Arrays.asList(new TimeWindow(at(12, 0), at(13, 0)))));
    }
}
