package closeai.application.autoschedule.engine;

import static closeai.application.autoschedule.ProblemFixtures.at;
import static closeai.application.autoschedule.ProblemFixtures.flatMatrix;
import static closeai.application.autoschedule.ProblemFixtures.noBlockedWindows;
import static closeai.application.autoschedule.ProblemFixtures.task;
import static closeai.application.autoschedule.ProblemFixtures.tasks;
import static closeai.application.autoschedule.ProblemFixtures.window;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.ScheduleTask;
import closeai.application.autoschedule.TimeWindow;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleEngineTest {

    private final ScheduleEngine engine = new ScheduleEngine();

    private ScheduleSearchResult search(ScheduleProblem problem) {
        return engine.search(problem, SearchBudget.defaultBudget());
    }

    @Test
    void placesEveryActivityExactlyOnce() {
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)),
                task("c", 60, 2, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 10));

        ScheduleSearchResult result = search(problem);

        assertTrue(result.isFound());
        assertEquals(3, result.getPlan().getPlacements().size());
        assertEquals(3, result.getPlan().orderedEventIds().stream().distinct().count());
    }

    @Test
    void respectsOpeningAndClosingHours() {
        List<ScheduleTask> items = tasks(
                task("late", 60, 0, at(18, 0), at(20, 0)),
                task("early", 60, 1, at(9, 0), at(11, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 10));

        ScheduleSearchResult result = search(problem);

        assertTrue(result.isFound());
        for (PlacedActivity placed : result.getPlan().getPlacements()) {
            assertFalse(placed.getStart().isBefore(placed.getTask().getOpeningTime()));
            assertFalse(placed.getEnd().isAfter(placed.getTask().getClosingTime()));
        }
        assertEquals(Arrays.asList("early", "late"), result.getPlan().orderedEventIds());
    }

    @Test
    void keepsEveryEventInsideTheAvailabilityWindow() {
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(0, 0), at(23, 59)),
                task("b", 60, 1, at(0, 0), at(23, 59)));
        TimeWindow availability = window(10, 13);
        ScheduleProblem problem = new ScheduleProblem(availability, items,
                noBlockedWindows(), flatMatrix(items, availability, 15));

        ScheduleSearchResult result = search(problem);

        assertTrue(result.isFound());
        for (PlacedActivity placed : result.getPlan().getPlacements()) {
            assertFalse(placed.getStart().isBefore(availability.getStart()));
            assertFalse(placed.getEnd().isAfter(availability.getEnd()));
        }
    }

    @Test
    void leavesEnoughTimeToTravelBetweenActivities() {
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 25));

        ScheduleSearchResult result = search(problem);

        assertTrue(result.isFound());
        List<PlacedActivity> placements = result.getPlan().getPlacements();
        PlacedActivity first = placements.get(0);
        PlacedActivity second = placements.get(1);
        assertEquals(25, second.getTravelMinutesBefore());
        assertFalse(second.getStart().isBefore(first.getEnd().plusMinutes(25)));
    }

    @Test
    void activitiesNeverOverlap() {
        List<ScheduleTask> items = tasks(
                task("a", 90, 0, at(9, 0), at(21, 0)),
                task("b", 90, 1, at(9, 0), at(21, 0)),
                task("c", 90, 2, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 10));

        ScheduleSearchResult result = search(problem);

        assertTrue(result.isFound());
        List<PlacedActivity> placements = result.getPlan().getPlacements();
        for (int i = 1; i < placements.size(); i++) {
            assertFalse(placements.get(i).getStart().isBefore(placements.get(i - 1).getEnd()));
        }
    }

    @Test
    void anActivityThatExactlyFitsIsStillScheduled() {
        List<ScheduleTask> items = tasks(task("a", 120, 0, at(9, 0), at(11, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 11), items,
                noBlockedWindows(), flatMatrix(items, window(9, 11), 10));

        ScheduleSearchResult result = search(problem);

        assertTrue(result.isFound());
        assertEquals(at(9, 0), result.getPlan().getPlacements().get(0).getStart());
        assertEquals(at(11, 0), result.getPlan().getPlacements().get(0).getEnd());
    }

    @Test
    void oneMinuteTooLongIsAConflict() {
        List<ScheduleTask> items = tasks(task("a", 121, 0, at(9, 0), at(11, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 11), items,
                noBlockedWindows(), flatMatrix(items, window(9, 11), 10));

        ScheduleSearchResult result = search(problem);

        assertFalse(result.isFound());
        assertEquals("a", result.getConflict().getBlockingEventId());
        assertTrue(result.getConflict().getReason().contains("121 minutes"));
    }

    @Test
    void waitingForAVenueToOpenIsNotCountedAsAvoidableIdle() {
        List<ScheduleTask> items = tasks(
                task("open-early", 60, 0, at(9, 0), at(21, 0)),
                task("opens-late", 60, 1, at(13, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 10));

        ScheduleSearchResult result = search(problem);

        assertTrue(result.isFound());
        PlacedActivity late = result.getPlan().getPlacements().get(1);
        assertEquals(at(13, 0), late.getStart());
        assertTrue(late.getIdleMinutesBefore() > 0);
        assertEquals(0, late.getAvoidableIdleMinutes(),
                "waiting for opening hours is unavoidable, not wasted time");
    }

    @Test
    void doesNotScheduleActivitiesDuringBlockedPeriods() {
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        List<TimeWindow> blocked = Arrays.asList(new TimeWindow(at(10, 0), at(12, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                blocked, flatMatrix(items, window(9, 21), 5));

        ScheduleSearchResult result = search(problem);

        assertTrue(result.isFound());
        for (PlacedActivity placed : result.getPlan().getPlacements()) {
            assertFalse(placed.window().overlaps(new TimeWindow(at(10, 0), at(12, 0))),
                    placed + " overlaps the user's blocked period");
        }
    }

    @Test
    void lockedActivitiesKeepTheirExactTimes() {
        List<ScheduleTask> items = tasks(
                task("museum", 60, 0, at(9, 0), at(21, 0)),
                closeai.application.autoschedule.ProblemFixtures.lockedTask(
                        "dinner", 90, 1, at(9, 0), at(22, 0), at(18, 30)),
                task("park", 60, 2, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 15));

        ScheduleSearchResult result = search(problem);

        assertTrue(result.isFound());
        PlacedActivity dinner = result.getPlan().getPlacements().stream()
                .filter(placed -> placed.getTask().getEventId().equals("dinner"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(at(18, 30), dinner.getStart());
        assertEquals(at(20, 0), dinner.getEnd());
        assertEquals(3, result.getPlan().getPlacements().size());
    }

    @Test
    void travelIntoALockedActivityMustFit() {
        // The lock starts 10 minutes after the only other activity can end, but travel is 45.
        List<ScheduleTask> items = tasks(
                task("far", 120, 0, at(9, 0), at(11, 0)),
                closeai.application.autoschedule.ProblemFixtures.lockedTask(
                        "locked", 60, 1, at(9, 0), at(22, 0), at(11, 10)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 45));

        ScheduleSearchResult result = search(problem);

        assertFalse(result.isFound(), "no order can absorb 45 minutes of travel into a fixed 11:10 lock");
    }

    @Test
    void reportsWhenTheNodeBudgetStoppedTheSearch() {
        List<ScheduleTask> items = tasks(
                task("a", 30, 0, at(9, 0), at(21, 0)),
                task("b", 30, 1, at(9, 0), at(21, 0)),
                task("c", 30, 2, at(9, 0), at(21, 0)),
                task("d", 30, 3, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 10));

        ScheduleSearchResult result = engine.search(problem, new SearchBudget(3));

        assertTrue(result.isFound(), "the greedy incumbent still provides a usable schedule");
        assertFalse(result.isCompletedWithinLimit(),
                "an exhausted budget must be reported, not presented as optimal");
    }

    @Test
    void aCompletedSearchReportsThatItFinished() {
        List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), flatMatrix(items, window(9, 21), 10));

        assertTrue(search(problem).isCompletedWithinLimit());
    }
}
