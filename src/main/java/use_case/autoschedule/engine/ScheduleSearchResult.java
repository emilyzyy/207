package use_case.autoschedule.engine;

import use_case.autoschedule.ScheduleConflict;
import use_case.autoschedule.SchedulePlan;

/**
 * Outcome of a search: either a plan or a conflict, never both, never an exception
 * for expected infeasibility.
 */
public final class ScheduleSearchResult {
    private final SchedulePlan plan;
    private final ScheduleConflict conflict;
    private final boolean completedWithinLimit;
    private final int nodesExplored;

    private ScheduleSearchResult(SchedulePlan plan, ScheduleConflict conflict,
                                 boolean completedWithinLimit, int nodesExplored) {
        this.plan = plan;
        this.conflict = conflict;
        this.completedWithinLimit = completedWithinLimit;
        this.nodesExplored = nodesExplored;
    }

    public static ScheduleSearchResult found(SchedulePlan plan, boolean completedWithinLimit,
                                             int nodesExplored) {
        return new ScheduleSearchResult(plan, null, completedWithinLimit, nodesExplored);
    }

    public static ScheduleSearchResult conflict(ScheduleConflict conflict, boolean completedWithinLimit,
                                                int nodesExplored) {
        return new ScheduleSearchResult(null, conflict, completedWithinLimit, nodesExplored);
    }

    public boolean isFound() {
        return plan != null;
    }

    public SchedulePlan getPlan() {
        return plan;
    }

    public ScheduleConflict getConflict() {
        return conflict;
    }

    /**
     * False when the node budget stopped the search early. The Preview then says
     * "best schedule found within the search limit" rather than claiming optimality.
     */
    public boolean isCompletedWithinLimit() {
        return completedWithinLimit;
    }

    public int getNodesExplored() {
        return nodesExplored;
    }
}
