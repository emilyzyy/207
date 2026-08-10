package use_case.autoschedule.engine;

import java.util.List;

import use_case.autoschedule.SchedulePlan;
import use_case.autoschedule.ScheduleProblem;
import use_case.autoschedule.ScheduleTask;

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
