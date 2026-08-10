package interface_adapter.viewmodels;

/** Before-and-after figures for the proposed schedule, ready to display. */
public final class PreviewMetricsView {

    private final int travelBeforeMinutes;
    private final int travelAfterMinutes;
    private final int idleBeforeMinutes;
    private final int idleAfterMinutes;
    private final int movedActivityCount;
    private final int activityCount;
    private final int practicalCostMinutes;

    public PreviewMetricsView(int travelBeforeMinutes, int travelAfterMinutes,
                              int idleBeforeMinutes, int idleAfterMinutes,
                              int movedActivityCount, int activityCount,
                              int practicalCostMinutes) {
        this.travelBeforeMinutes = travelBeforeMinutes;
        this.travelAfterMinutes = travelAfterMinutes;
        this.idleBeforeMinutes = idleBeforeMinutes;
        this.idleAfterMinutes = idleAfterMinutes;
        this.movedActivityCount = movedActivityCount;
        this.activityCount = activityCount;
        this.practicalCostMinutes = practicalCostMinutes;
    }

    public int getTravelBeforeMinutes() {
        return travelBeforeMinutes;
    }

    public int getTravelAfterMinutes() {
        return travelAfterMinutes;
    }

    public int getIdleBeforeMinutes() {
        return idleBeforeMinutes;
    }

    public int getIdleAfterMinutes() {
        return idleAfterMinutes;
    }

    public int getMovedActivityCount() {
        return movedActivityCount;
    }

    public int getActivityCount() {
        return activityCount;
    }
    /**
     * Travel, wasted waiting and capped soft penalties, added together.
     * @return the result of the operation
     */

    public int getPracticalCostMinutes() {
        return practicalCostMinutes;
    }

    public int getTravelSavedMinutes() {
        return travelBeforeMinutes - travelAfterMinutes;
    }

    public int getIdleSavedMinutes() {
        return idleBeforeMinutes - idleAfterMinutes;
    }
}
