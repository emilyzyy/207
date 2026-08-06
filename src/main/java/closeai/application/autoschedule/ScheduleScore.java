package closeai.application.autoschedule;

import java.util.Objects;

/**
 * Lexicographic quality of a schedule, better meaning smaller.
 *
 * <p>Tiers in order: enabled soft-policy penalty, total travel minutes, avoidable
 * idle minutes, original-order disruption, then a stable identifier tie-break that
 * guarantees the same input always yields the same output.</p>
 *
 * <p>Penalties are expressed in a common "equivalent wasted minutes" unit so that no
 * policy can dominate the others merely by choosing a larger scale.</p>
 */
public final class ScheduleScore implements Comparable<ScheduleScore> {
    private final int policyPenalty;
    private final int travelMinutes;
    private final int avoidableIdleMinutes;
    private final int orderDisruption;
    private final String tieBreak;

    public ScheduleScore(int policyPenalty, int travelMinutes, int avoidableIdleMinutes,
                         int orderDisruption, String tieBreak) {
        this.policyPenalty = policyPenalty;
        this.travelMinutes = travelMinutes;
        this.avoidableIdleMinutes = avoidableIdleMinutes;
        this.orderDisruption = orderDisruption;
        this.tieBreak = tieBreak == null ? "" : tieBreak;
    }

    public int getPolicyPenalty() {
        return policyPenalty;
    }

    public int getTravelMinutes() {
        return travelMinutes;
    }

    public int getAvoidableIdleMinutes() {
        return avoidableIdleMinutes;
    }

    public int getOrderDisruption() {
        return orderDisruption;
    }

    public String getTieBreak() {
        return tieBreak;
    }

    @Override
    public int compareTo(ScheduleScore other) {
        int result = Integer.compare(policyPenalty, other.policyPenalty);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(travelMinutes, other.travelMinutes);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(avoidableIdleMinutes, other.avoidableIdleMinutes);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(orderDisruption, other.orderDisruption);
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
        return Objects.hash(policyPenalty, travelMinutes, avoidableIdleMinutes,
                orderDisruption, tieBreak);
    }

    @Override
    public String toString() {
        return "score[" + policyPenalty + "," + travelMinutes + "," + avoidableIdleMinutes
                + "," + orderDisruption + "," + tieBreak + "]";
    }
}
