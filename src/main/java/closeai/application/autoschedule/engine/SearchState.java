package closeai.application.autoschedule.engine;

import closeai.application.autoschedule.SchedulePlan;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.ScheduleTask;
import java.util.List;

/** Mutable bookkeeping shared across one search: the incumbent and the node budget. */
final class SearchState {
    final ScheduleProblem problem;
    final List<ScheduleTask> locked;
    final SearchBudget budget;
    SchedulePlan best;

    SearchState(ScheduleProblem problem, List<ScheduleTask> locked, SearchBudget budget) {
        this.problem = problem;
        this.locked = locked;
        this.budget = budget;
    }
}
