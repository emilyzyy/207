package closeai.application.autoschedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the Preview screen needs, and nothing else.
 *
 * <p>Deliberately free of entities: the Presenter receives display-ready values rather
 * than a {@code Trip} it could read stale state from or mutate. The reasons travel as
 * codes, leaving the wording to the Presenter.</p>
 */
public final class AutoSchedulePreviewOutputData {

    private final List<ProposedEventData> rows;
    private final int travelBeforeMinutes;
    private final int travelAfterMinutes;
    private final int idleBeforeMinutes;
    private final int idleAfterMinutes;
    private final int movedActivityCount;
    private final int activityCount;
    private final List<Reason> reasons;
    private final List<String> warnings;
    private final List<PolicyId> activePolicies;
    private final String scheduleFingerprint;
    private final boolean searchCompletedWithinLimit;
    private final TravelEstimateQuality travelQuality;
    private final boolean keptCurrentOrder;
    private final int practicalCostMinutes;

    public AutoSchedulePreviewOutputData(List<ProposedEventData> rows,
                                         int travelBeforeMinutes, int travelAfterMinutes,
                                         int idleBeforeMinutes, int idleAfterMinutes,
                                         int movedActivityCount, int activityCount,
                                         List<Reason> reasons, List<String> warnings,
                                         List<PolicyId> activePolicies,
                                         String scheduleFingerprint,
                                         boolean searchCompletedWithinLimit,
                                         TravelEstimateQuality travelQuality,
                                         boolean keptCurrentOrder,
                                         int practicalCostMinutes) {
        this.keptCurrentOrder = keptCurrentOrder;
        this.practicalCostMinutes = practicalCostMinutes;
        this.rows = copy(rows);
        this.travelBeforeMinutes = travelBeforeMinutes;
        this.travelAfterMinutes = travelAfterMinutes;
        this.idleBeforeMinutes = idleBeforeMinutes;
        this.idleAfterMinutes = idleAfterMinutes;
        this.movedActivityCount = movedActivityCount;
        this.activityCount = activityCount;
        this.reasons = copy(reasons);
        this.warnings = copy(warnings);
        this.activePolicies = copy(activePolicies);
        this.scheduleFingerprint = scheduleFingerprint == null ? "" : scheduleFingerprint;
        this.searchCompletedWithinLimit = searchCompletedWithinLimit;
        this.travelQuality = travelQuality == null ? TravelEstimateQuality.UNKNOWN : travelQuality;
    }

    private static <T> List<T> copy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(
                source == null ? Collections.<T>emptyList() : source));
    }

    public List<ProposedEventData> getRows() {
        return rows;
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

    public List<Reason> getReasons() {
        return reasons;
    }

    /** Things the user should know that did not stop the schedule being produced. */
    public List<String> getWarnings() {
        return warnings;
    }

    public List<PolicyId> getActivePolicies() {
        return activePolicies;
    }

    public String getScheduleFingerprint() {
        return scheduleFingerprint;
    }

    /**
     * False when the node budget stopped the search early, so the Preview can say
     * "best found within the search limit" instead of implying it proved anything.
     */
    public boolean isSearchCompletedWithinLimit() {
        return searchCompletedWithinLimit;
    }

    /**
     * The weakest confidence behind any travel number shown. UNKNOWN is reported
     * honestly rather than dressed up as routed, because the shared routing port
     * cannot distinguish a real route from its own distance-based fallback.
     */
    public TravelEstimateQuality getTravelQuality() {
        return travelQuality;
    }

    /** Whether the traveller asked to keep the order they had arranged. */
    public boolean isKeptCurrentOrder() {
        return keptCurrentOrder;
    }

    /**
     * The schedule's total cost in minutes: travel, wasted waiting and the capped soft
     * penalties added together. Shown so the Preview can be specific about what improved.
     */
    public int getPracticalCostMinutes() {
        return practicalCostMinutes;
    }
}
