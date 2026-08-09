package use_case.autoschedule.engine;

import use_case.autoschedule.ScheduleProblem;
import use_case.autoschedule.ScheduleTask;
import java.time.LocalTime;

/**
 * An extra feasibility veto consulted when the engine places an activity.
 *
 * <p>The engine builds in the stable physics — day bounds, opening hours, travel
 * arithmetic, overlap, locks — because those rules are interdependent and drive
 * pruning. This interface exists for genuinely optional restrictions that can be
 * added later (a maximum walking leg, a preparation buffer) without editing the
 * search: extending behaviour without modifying the class.</p>
 */
public interface PlacementRule {

    /** True when the activity may occupy {@code [start, end)} in this problem. */
    boolean allows(ScheduleProblem problem, ScheduleTask task,
                   LocalTime start, LocalTime end, int travelMinutesBefore);
}
