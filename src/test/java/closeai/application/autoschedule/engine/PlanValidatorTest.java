package closeai.application.autoschedule.engine;

import static closeai.application.autoschedule.ProblemFixtures.at;
import static closeai.application.autoschedule.ProblemFixtures.flatMatrix;
import static closeai.application.autoschedule.ProblemFixtures.lockedTask;
import static closeai.application.autoschedule.ProblemFixtures.noBlockedWindows;
import static closeai.application.autoschedule.ProblemFixtures.task;
import static closeai.application.autoschedule.ProblemFixtures.tasks;
import static closeai.application.autoschedule.ProblemFixtures.window;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.SchedulePlan;
import closeai.application.autoschedule.ScheduleConflict;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.ScheduleTask;
import closeai.application.autoschedule.SchedulingPreferences;
import closeai.application.autoschedule.TimeWindow;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The validator is the last thing standing between a wrong schedule and the traveller.
 *
 * <p>It re-checks a finished plan against every hard rule, independently of whatever
 * built it, because the times finally shown are not the times the search reasoned about.
 * These tests feed it deliberately broken plans - the kind a bug elsewhere would produce
 * - and require each one to be caught.
 */
class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();

    private static ScheduleProblem problemFor(List<ScheduleTask> items,
                                              List<TimeWindow> blocked) {
        return new ScheduleProblem(window(9, 21), items, blocked,
                flatMatrix(items, window(9, 21), 10), SchedulingPreferences.none());
    }

    private static PlacedActivity placed(ScheduleTask task, LocalTime start,
                                         LocalTime travelDeparture, int travelMinutes) {
        return new PlacedActivity(task, start, start.plusMinutes(task.getDurationMinutes()),
                travelDeparture, travelMinutes, 0, 0);
    }

    private static SchedulePlan planOf(PlacedActivity... placements) {
        return new SchedulePlan(Arrays.asList(placements),
                ScheduleEngine.score(Arrays.asList(placements), SchedulingPreferences.none()));
    }

    @Test
    void aSoundPlanPasses() {
        ScheduleTask first = task("a", 60, 0, at(9, 0), at(21, 0));
        ScheduleTask second = task("b", 60, 1, at(9, 0), at(21, 0));
        ScheduleProblem problem = problemFor(tasks(first, second), noBlockedWindows());

        SchedulePlan plan = planOf(placed(first, at(9, 0), null, 0),
                placed(second, at(10, 10), at(10, 0), 10));

        assertNull(validator.validate(problem, plan));
    }

    @Test
    void aMissingPlanIsAConflictRatherThanACrash() {
        ScheduleProblem problem = problemFor(
                tasks(task("a", 60, 0, at(9, 0), at(21, 0))), noBlockedWindows());

        assertNotNull(validator.validate(problem, null));
    }

    @Test
    void anActivityLeftOutIsCaught() {
        ScheduleTask first = task("a", 60, 0, at(9, 0), at(21, 0));
        ScheduleTask second = task("b", 60, 1, at(9, 0), at(21, 0));
        ScheduleProblem problem = problemFor(tasks(first, second), noBlockedWindows());

        ScheduleConflict conflict = validator.validate(problem,
                planOf(placed(first, at(9, 0), null, 0)));

        assertNotNull(conflict, "every activity must appear; one was dropped");
        assertEquals("b", conflict.getBlockingEventId());
    }

    @Test
    void anActivityScheduledTwiceIsCaught() {
        ScheduleTask only = task("a", 60, 0, at(9, 0), at(21, 0));
        ScheduleProblem problem = problemFor(tasks(only), noBlockedWindows());

        ScheduleConflict conflict = validator.validate(problem,
                planOf(placed(only, at(9, 0), null, 0), placed(only, at(11, 0), at(10, 0), 10)));

        assertNotNull(conflict);
        assertEquals("a", conflict.getBlockingEventId());
    }

    @Test
    void anActivityOutsideTheAvailableHoursIsCaught() {
        ScheduleTask only = task("a", 60, 0, at(0, 0), at(23, 59));
        ScheduleProblem problem = problemFor(tasks(only), noBlockedWindows());

        assertNotNull(validator.validate(problem, planOf(placed(only, at(22, 0), null, 0))),
                "22:00 is outside the 09:00-21:00 window");
    }

    @Test
    void anActivityOutsideItsOpeningHoursIsCaught() {
        ScheduleTask only = task("a", 60, 0, at(14, 0), at(16, 0));
        ScheduleProblem problem = problemFor(tasks(only), noBlockedWindows());

        assertNotNull(validator.validate(problem, planOf(placed(only, at(10, 0), null, 0))),
                "the venue is shut at 10:00");
    }

    @Test
    void anActivityOverlappingAnUnavailablePeriodIsCaught() {
        ScheduleTask only = task("a", 60, 0, at(9, 0), at(21, 0));
        ScheduleProblem problem = problemFor(tasks(only),
                Arrays.asList(new TimeWindow(at(12, 0), at(13, 0))));

        ScheduleConflict conflict = validator.validate(problem,
                planOf(placed(only, at(12, 30), null, 0)));

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.LOCK_INSIDE_UNAVAILABLE_PERIOD, conflict.getKind());
    }

    @Test
    void travelRunningThroughAnUnavailablePeriodIsCaught() {
        ScheduleTask first = task("a", 60, 0, at(9, 0), at(21, 0));
        ScheduleTask second = task("b", 60, 1, at(9, 0), at(21, 0));
        ScheduleProblem problem = problemFor(tasks(first, second),
                Arrays.asList(new TimeWindow(at(10, 0), at(11, 0))));

        // The journey leaves at 10:00, straight into the blocked hour.
        ScheduleConflict conflict = validator.validate(problem,
                planOf(placed(first, at(9, 0), null, 0),
                        placed(second, at(11, 30), at(10, 0), 30)));

        assertNotNull(conflict, "a journey may not run through an unavailable period");
    }

    @Test
    void anActivityStartingBeforeItsTravelHasArrivedIsCaught() {
        ScheduleTask first = task("a", 60, 0, at(9, 0), at(21, 0));
        ScheduleTask second = task("b", 60, 1, at(9, 0), at(21, 0));
        ScheduleProblem problem = problemFor(tasks(first, second), noBlockedWindows());

        // 40 minutes of travel leaving at 10:00 cannot deliver anyone by 10:20.
        ScheduleConflict conflict = validator.validate(problem,
                planOf(placed(first, at(9, 0), null, 0),
                        placed(second, at(10, 20), at(10, 0), 40)));

        assertNotNull(conflict);
        assertEquals(ScheduleConflict.Kind.REFINED_TRAVEL_INFEASIBLE, conflict.getKind());
    }

    @Test
    void twoActivitiesOverlappingEachOtherIsCaught() {
        ScheduleTask first = task("a", 60, 0, at(9, 0), at(21, 0));
        ScheduleTask second = task("b", 60, 1, at(9, 0), at(21, 0));
        ScheduleProblem problem = problemFor(tasks(first, second), noBlockedWindows());

        ScheduleConflict conflict = validator.validate(problem,
                planOf(placed(first, at(9, 0), null, 0),
                        placed(second, at(9, 30), at(9, 0), 0)));

        assertNotNull(conflict, "two activities cannot happen at once");
    }

    @Test
    void travelStartingBeforeThePreviousActivityEndsIsCaught() {
        ScheduleTask first = task("a", 60, 0, at(9, 0), at(21, 0));
        ScheduleTask second = task("b", 60, 1, at(9, 0), at(21, 0));
        ScheduleProblem problem = problemFor(tasks(first, second), noBlockedWindows());

        // Leaving at 09:30 while the first activity runs until 10:00.
        ScheduleConflict conflict = validator.validate(problem,
                planOf(placed(first, at(9, 0), null, 0),
                        placed(second, at(10, 30), at(9, 30), 20)));

        assertNotNull(conflict, "nobody can set off before they have finished");
    }

    @Test
    void aLockedActivityMovedFromItsPinnedTimeIsCaught() {
        ScheduleTask locked = lockedTask("dinner", 60, 0, at(9, 0), at(22, 0), at(18, 0));
        ScheduleProblem problem = problemFor(tasks(locked), noBlockedWindows());

        ScheduleConflict conflict = validator.validate(problem,
                planOf(placed(locked, at(15, 0), null, 0)));

        assertNotNull(conflict, "a pinned activity must stay pinned");
    }

    @Test
    void aLockedActivityLeftAtItsPinnedTimePasses() {
        ScheduleTask locked = lockedTask("dinner", 60, 0, at(9, 0), at(22, 0), at(18, 0));
        ScheduleProblem problem = problemFor(tasks(locked), noBlockedWindows());

        assertNull(validator.validate(problem, planOf(placed(locked, at(18, 0), null, 0))));
    }

    @Test
    void anEmptyBlockedListIsHandledWithoutSpecialCasing() {
        ScheduleTask only = task("a", 60, 0, at(9, 0), at(21, 0));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), tasks(only),
                Collections.emptyList(), flatMatrix(tasks(only), window(9, 21), 10));

        assertNull(validator.validate(problem, planOf(placed(only, at(9, 0), null, 0))));
    }
}
