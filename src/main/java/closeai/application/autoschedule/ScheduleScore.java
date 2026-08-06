package closeai.application.autoschedule;

import java.util.Objects;

/**
 * How good a valid schedule is, as a single practical cost in minutes. Lower is better.
 *
 * <p>Every consideration is expressed in the same unit — minutes the traveller would
 * rather not spend — and simply added up: time spent travelling, time wasted waiting
 * around, and capped penalties for eating at odd hours, being outdoors after dark, or
 * standing in bad weather.</p>
 *
 * <p>Adding rather than ranking in tiers is a deliberate correction. With strict tiers a
 * trivial improvement in one consideration could outrank any amount of extra travel, so
 * the schedule could send someone across the city to shift lunch by ten minutes. Because
 * each soft penalty is capped, a soft improvement can never justify more extra travel
 * than its own cap, which is a property the tests pin down directly.</p>
 *
 * <p>The identifier tie-break at the end guarantees that equally good schedules always
 * resolve the same way, so the same input produces the same output every time.</p>
 */
public final class ScheduleScore implements Comparable<ScheduleScore> {

    private final int travelMinutes;
    private final int avoidableIdleMinutes;
    private final int policyPenaltyMinutes;
    private final int orderPenaltyMinutes;
    private final String tieBreak;

    public ScheduleScore(int travelMinutes, int avoidableIdleMinutes, int policyPenaltyMinutes,
                         int orderPenaltyMinutes, String tieBreak) {
        this.travelMinutes = travelMinutes;
        this.avoidableIdleMinutes = avoidableIdleMinutes;
        this.policyPenaltyMinutes = policyPenaltyMinutes;
        this.orderPenaltyMinutes = orderPenaltyMinutes;
        this.tieBreak = tieBreak == null ? "" : tieBreak;
    }

    /** The whole objective: everything the traveller would rather not spend, added up. */
    public int practicalCostMinutes() {
        return travelMinutes + avoidableIdleMinutes + policyPenaltyMinutes + orderPenaltyMinutes;
    }

    public int getTravelMinutes() {
        return travelMinutes;
    }

    public int getAvoidableIdleMinutes() {
        return avoidableIdleMinutes;
    }

    /** Capped meal, daylight and weather penalties combined. */
    public int getPolicyPenaltyMinutes() {
        return policyPenaltyMinutes;
    }

    /**
     * A small, capped charge for rearranging the user's original order, added only when
     * they ask to keep it. Being capped is what keeps it a near-tie consideration rather
     * than something that could outweigh a genuinely better day.
     */
    public int getOrderPenaltyMinutes() {
        return orderPenaltyMinutes;
    }

    public String getTieBreak() {
        return tieBreak;
    }

    @Override
    public int compareTo(ScheduleScore other) {
        int result = Integer.compare(practicalCostMinutes(), other.practicalCostMinutes());
        if (result != 0) {
            return result;
        }
        return tieBreak.compareTo(other.tieBreak);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScheduleScore)) {
            return false;
        }
        return compareTo((ScheduleScore) other) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(practicalCostMinutes(), tieBreak);
    }

    @Override
    public String toString() {
        return "cost=" + practicalCostMinutes() + "min(travel=" + travelMinutes
                + ",idle=" + avoidableIdleMinutes + ",soft=" + policyPenaltyMinutes
                + ",order=" + orderPenaltyMinutes + ")";
    }
}
