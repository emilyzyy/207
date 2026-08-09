package trippy.application.autoschedule;

import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bucketed travel estimates for one run: every directed leg, for every active
 * {@link DeparturePeriod}. Built before the search and read inside it, so the
 * recursion performs no network calls.
 */
public final class TravelMatrix {
    private final PeriodPlan periods;
    private final Map<PeriodLeg, TravelEstimate> legs;

    private TravelMatrix(PeriodPlan periods, Map<PeriodLeg, TravelEstimate> legs) {
        this.periods = periods;
        this.legs = legs;
    }

    public static Builder builder(PeriodPlan periods) {
        return new Builder(periods);
    }

    public PeriodPlan getPeriods() {
        return periods;
    }

    /**
     * Travel for the leg {@code fromId -> toId} departing at {@code departure},
     * read from the bucket containing that departure.
     */
    public TravelEstimate estimateAt(String fromId, String toId, LocalTime departure) {
        return require(fromId, toId, periods.resolve(departure));
    }

    /**
     * Smallest travel time for this leg across every active period.
     *
     * <p>This is what keeps the search's lower bound admissible: because no bucketed
     * value is ever below it, a bound built from these minima can never overestimate
     * the remaining cost, so pruning cannot discard a branch that might still be
     * optimal.</p>
     */
    public int minMinutes(String fromId, String toId) {
        int best = Integer.MAX_VALUE;
        for (DeparturePeriod period : periods.activePeriods()) {
            TravelEstimate estimate = legs.get(new PeriodLeg(fromId, toId, period));
            if (estimate != null && estimate.getMinutes() < best) {
                best = estimate.getMinutes();
            }
        }
        if (best == Integer.MAX_VALUE) {
            throw new IllegalStateException(missingMessage(fromId, toId));
        }
        return best;
    }

    /**
     * Cheapest way to arrive at {@code toId} from any of {@code candidateFromIds},
     * minimised over periods. Used by the search's remaining-travel relaxation.
     */
    public int minIncomingMinutes(String toId, Collection<String> candidateFromIds) {
        int best = Integer.MAX_VALUE;
        for (String fromId : candidateFromIds) {
            if (fromId.equals(toId)) {
                continue;
            }
            best = Math.min(best, minMinutes(fromId, toId));
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    /** The weakest confidence of any leg held here, for honest Preview disclosure. */
    public TravelEstimateQuality weakestQuality() {
        TravelEstimateQuality weakest = TravelEstimateQuality.ROUTED;
        for (TravelEstimate estimate : legs.values()) {
            if (estimate.getQuality() == TravelEstimateQuality.ESTIMATED) {
                return TravelEstimateQuality.ESTIMATED;
            }
            if (estimate.getQuality() == TravelEstimateQuality.UNKNOWN) {
                weakest = TravelEstimateQuality.UNKNOWN;
            }
        }
        return weakest;
    }

    /** True when every leg in this matrix came from a live routing answer. */
    public boolean allRouted() {
        return weakestQuality() == TravelEstimateQuality.ROUTED;
    }

    /**
     * A copy with exact-departure values written over the buckets they fall in,
     * used between refinement rounds so the next search sees the real numbers.
     */
    public TravelMatrix withOverrides(Map<TravelLegKey, TravelEstimate> overrides) {
        Map<PeriodLeg, TravelEstimate> updated = new HashMap<>(legs);
        for (Map.Entry<TravelLegKey, TravelEstimate> entry : overrides.entrySet()) {
            TravelLegKey leg = entry.getKey();
            updated.put(new PeriodLeg(leg.getFromId(), leg.getToId(),
                    periods.resolve(leg.getDeparture())), entry.getValue());
        }
        return new TravelMatrix(periods, updated);
    }

    public int legCount() {
        return legs.size();
    }

    private TravelEstimate require(String fromId, String toId, DeparturePeriod period) {
        TravelEstimate estimate = legs.get(new PeriodLeg(fromId, toId, period));
        if (estimate == null) {
            throw new IllegalStateException(missingMessage(fromId, toId));
        }
        return estimate;
    }

    private static String missingMessage(String fromId, String toId) {
        return "No travel estimate prefetched for " + fromId + " to " + toId;
    }

    /** Collects prefetched estimates for one run. */
    public static final class Builder {
        private final PeriodPlan periods;
        private final Map<PeriodLeg, TravelEstimate> legs = new HashMap<>();

        private Builder(PeriodPlan periods) {
            if (periods == null) {
                throw new IllegalArgumentException("Period plan is required");
            }
            this.periods = periods;
        }

        public Builder put(String fromId, String toId, DeparturePeriod period,
                           TravelEstimate estimate) {
            if (fromId == null || toId == null || period == null || estimate == null) {
                throw new IllegalArgumentException("Travel matrix entry is incomplete");
            }
            legs.put(new PeriodLeg(fromId, toId, period), estimate);
            return this;
        }

        public TravelMatrix build() {
            return new TravelMatrix(periods, new HashMap<>(legs));
        }
    }

    /**
     * Map key for one directed leg in one period. A typed key avoids inventing a
     * string delimiter that an activity identifier might itself contain.
     */
    private static final class PeriodLeg {
        private final String fromId;
        private final String toId;
        private final DeparturePeriod period;

        PeriodLeg(String fromId, String toId, DeparturePeriod period) {
            this.fromId = fromId;
            this.toId = toId;
            this.period = period;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PeriodLeg)) {
                return false;
            }
            PeriodLeg that = (PeriodLeg) other;
            return period == that.period && fromId.equals(that.fromId) && toId.equals(that.toId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fromId, toId, period);
        }
    }
}
