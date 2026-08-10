package use_case.autoschedule;

import static use_case.autoschedule.ProblemFixtures.at;
import static use_case.autoschedule.ProblemFixtures.flatMatrix;
import static use_case.autoschedule.ProblemFixtures.lockedTask;
import static use_case.autoschedule.ProblemFixtures.task;
import static use_case.autoschedule.ProblemFixtures.tasks;
import static use_case.autoschedule.ProblemFixtures.window;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.engine.ScheduleSearchResult;
import use_case.autoschedule.engine.SearchBudget;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unavailable windows are inviolable: the user has said they are unavailable for any
 * itinerary event, so neither an activity nor a journey may run through one.
 */
class UnavailableWindowTest {

    private final ScheduleEngine engine = new ScheduleEngine();
    private final ProblemValidator validator = new ProblemValidator();

    @Test
    void noActivityOverlapsAnUnavailableWindow() {
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        TimeWindow blocked = new TimeWindow(at(10, 0), at(12, 0));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(blocked), flatMatrix(items, window(9, 21), 5));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound());
        for (PlacedActivity placed : result.getPlan().getPlacements()) {
            assertFalse(placed.window().overlaps(blocked), placed + " overlaps a blocked period");
        }
    }

    @Test
    void noTravelBlockRunsThroughAnUnavailableWindow() {
        // Leaving "a" at 10:00 would travel 10:00-10:40, straight through the block.
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        TimeWindow blocked = new TimeWindow(at(10, 0), at(12, 0));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(blocked), flatMatrix(items, window(9, 21), 40));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound());
        PlacedActivity second = result.getPlan().getPlacements().get(1);
        assertNotNull(second.travelWindow());
        assertFalse(second.travelWindow().overlaps(blocked),
                "travel " + second.travelWindow() + " runs through the blocked period");
    }

    @Test
    void theTravellerWaitsForTheWindowToEndBeforeSettingOut() {
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        TimeWindow blocked = new TimeWindow(at(10, 0), at(12, 0));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(blocked), flatMatrix(items, window(9, 21), 40));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());
        PlacedActivity second = result.getPlan().getPlacements().get(1);

        assertEquals(at(12, 0), second.getTravelDeparture(),
                "the journey should start when the blocked period ends");
        assertEquals(at(12, 40), second.getStart());
    }

    @Test
    void travelMayEndExactlyWhenUnavailableTimeBegins() {
        BlockedPeriods blocked = BlockedPeriods.of(
                Arrays.asList(new TimeWindow(at(10, 30), at(13, 0))));

        assertFalse(blocked.blocks(at(10, 0), at(10, 30)),
                "unavailable windows are half-open: touching the start is not overlap");
    }

    @Test
    void travelMayBeginExactlyWhenUnavailableTimeEnds() {
        BlockedPeriods blocked = BlockedPeriods.of(
                Arrays.asList(new TimeWindow(at(10, 30), at(13, 0))));

        assertFalse(blocked.blocks(at(13, 0), at(13, 20)),
                "the traveller becomes available at the window's exact end");
    }

    /**
     * Reaching the destination before an appointment and waiting there through it is not a
     * legal substitute for travelling afterwards. The destination is movable, so the journey
     * and the visit must move together.
     */
    @Test
    void anUnlockedDestinationMovesWithTravelToAfterAnUnavailablePeriod() {
        List<ScheduleTask> items = tasks(
                lockedTask("a", 60, 0, at(9, 0), at(21, 0), at(9, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        TimeWindow blocked = new TimeWindow(at(10, 30), at(13, 0));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(blocked), flatMatrix(items, window(9, 21), 20));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound());
        PlacedActivity destination = result.getPlan().getPlacements().get(1);
        assertEquals(at(13, 0), destination.getTravelDeparture(),
                "travel should begin when the unavailable period ends");
        assertEquals(at(13, 20), destination.getStart(),
                "the unlocked destination must move with its 20-minute journey");
        assertEquals(destination.getStart(), destination.travelWindow().getEnd(),
                "the traveller should arrive just in time, not wait at the destination");
    }

    /** A locked destination cannot move, so arriving before and waiting through a block fails. */
    @Test
    void aLockedDestinationAfterTheBlockIsAConflictWhenTravelCannotFitJustBeforeIt() {
        List<ScheduleTask> items = tasks(
                lockedTask("a", 60, 0, at(9, 0), at(21, 0), at(9, 0)),
                lockedTask("b", 60, 1, at(9, 0), at(21, 0), at(13, 0)));
        TimeWindow blocked = new TimeWindow(at(10, 30), at(13, 0));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(blocked), flatMatrix(items, window(9, 21), 20));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertFalse(result.isFound(),
                "a locked 1:00 PM destination cannot be reached by travelling through the block "
                        + "or by waiting at the destination from 10:30 AM");
    }

    @Test
    void waitingOutABlockedPeriodIsNotCountedAsWastedTime() {
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(new TimeWindow(at(10, 0), at(12, 0))),
                flatMatrix(items, window(9, 21), 5));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());
        PlacedActivity second = result.getPlan().getPlacements().get(1);

        assertTrue(second.getIdleMinutesBefore() > 0);
        assertEquals(0, second.getAvoidableIdleMinutes(),
                "time the user declared unavailable is not idle the schedule could reclaim");
    }

    @Test
    void aDayWithNoRoomLeftAroundTheBlockIsAConflict() {
        List<ScheduleTask> items = tasks(
                task("a", 120, 0, at(9, 0), at(21, 0)),
                task("b", 120, 1, at(9, 0), at(21, 0)));
        // 09:00-21:00 with a 10-hour block leaves two hours for two two-hour activities.
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                Arrays.asList(new TimeWindow(at(11, 0), at(21, 0))),
                flatMatrix(items, window(9, 21), 10));

        ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertFalse(result.isFound());
    }

    @Test
    void overlappingUnavailableWindowsAreRejectedRatherThanMerged() {
        List<ScheduleTask> items = tasks(task("a", 60, 0, at(9, 0), at(21, 0)));
        List<TimeWindow> overlapping = Arrays.asList(
                new TimeWindow(at(12, 0), at(13, 0)),
                new TimeWindow(at(12, 30), at(14, 0)));

        ScheduleConflict conflict = validator.validate(window(9, 21), items, overlapping);

        assertNotNull(conflict, "overlapping windows are a typo worth surfacing, not merging");
        assertEquals(ScheduleConflict.Kind.LOCKS_OVERLAP, conflict.getKind());
    }

    @Test
    void anUnavailableWindowOutsideAvailabilityIsRejected() {
        List<ScheduleTask> items = tasks(task("a", 60, 0, at(9, 0), at(21, 0)));

        ScheduleConflict conflict = validator.validate(window(9, 17), items,
                Arrays.asList(new TimeWindow(at(18, 0), at(19, 0))));

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_OUTSIDE_AVAILABILITY, conflict.getKind());
    }

    @Test
    void aLockedActivityInsideAnUnavailableWindowIsAConflict() {
        List<ScheduleTask> items = tasks(
                lockedTask("dinner", 60, 0, at(9, 0), at(22, 0), at(12, 30)));

        ScheduleConflict conflict = validator.validate(window(9, 21), items,
                Arrays.asList(new TimeWindow(at(12, 0), at(13, 0))));

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_INSIDE_UNAVAILABLE_PERIOD, conflict.getKind());
        assertEquals("dinner", conflict.getBlockingEventId());
        assertEquals("lock 12:30-13:30; unavailable 12:00-13:00", conflict.getDetail(),
                "the Presenter needs both exact windows; the validator must not discard them");
    }

    @Test
    void aValidRequestPassesValidation() {
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                lockedTask("dinner", 90, 1, at(9, 0), at(22, 0), at(18, 30)));

        assertNull(validator.validate(window(9, 21), items,
                Arrays.asList(new TimeWindow(at(12, 0), at(13, 0)))));
    }
}
