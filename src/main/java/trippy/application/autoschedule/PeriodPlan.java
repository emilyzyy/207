package trippy.application.autoschedule;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which {@link DeparturePeriod}s are actually fetched for one Autoschedule
 * run, and maps any departure time onto one of them.
 *
 * <p>Three forces shape the plan:</p>
 * <ol>
 *   <li>only periods overlapping the availability window are worth fetching;</li>
 *   <li>modes whose provider has no time input collapse to a single bucket, so a
 *       walking run costs exactly one matrix;</li>
 *   <li>the prefetch is capped at {@link #MAX_PREFETCH_CALLS} requests, and when the
 *       cap is exceeded periods merge along a fixed ladder so the degradation is
 *       deterministic and reportable rather than silent.</li>
 * </ol>
 */
public final class PeriodPlan {

    /**
     * Target ceiling on {@code periods * directedPairs} prefetch requests per run.
     *
     * <p>This governs how many departure periods are worth fetching, not the total on its
     * own. Scheduling cannot begin without knowing the travel time between every pair of
     * activities, so one full matrix - {@code directedPairs} requests - is an irreducible
     * floor. Once periods have collapsed to one, a day large enough to exceed this figure
     * still costs that single matrix; the alternative would be refusing to schedule at
     * all. The invariant that actually holds is therefore: either a single period is in
     * use, or the total sits within this ceiling.</p>
     */
    public static final int MAX_PREFETCH_CALLS = 120;

    /**
     * Fixed merge order applied while the prefetch budget is exceeded. PEAK is kept
     * distinct longest because rush-hour is the variation the buckets exist to capture.
     */
    private static final DeparturePeriod[][] MERGE_LADDER = {
        {DeparturePeriod.LATE, DeparturePeriod.PEAK},
        {DeparturePeriod.EARLY, DeparturePeriod.MIDDAY},
        {DeparturePeriod.PEAK, DeparturePeriod.MIDDAY},
    };

    private final Map<DeparturePeriod, DeparturePeriod> assignment;
    private final List<DeparturePeriod> active;

    private PeriodPlan(Map<DeparturePeriod, DeparturePeriod> assignment,
                       List<DeparturePeriod> active) {
        this.assignment = assignment;
        this.active = active;
    }

    /**
     * Builds the plan for one run.
     *
     * @param window          availability window for the run
     * @param timeSensitive   whether the provider varies with departure time
     * @param directedPairs   number of directed legs that will be prefetched
     */
    public static PeriodPlan forRun(TimeWindow window, boolean timeSensitive, int directedPairs) {
        return forRun(window, timeSensitive, directedPairs, MAX_PREFETCH_CALLS);
    }

    static PeriodPlan forRun(TimeWindow window, boolean timeSensitive,
                             int directedPairs, int maxPrefetchCalls) {
        if (window == null) {
            throw new IllegalArgumentException("Availability window is required");
        }
        if (directedPairs < 0) {
            throw new IllegalArgumentException("Directed pair count cannot be negative");
        }

        List<DeparturePeriod> overlapping = overlapping(window);
        Map<DeparturePeriod, DeparturePeriod> map = new EnumMap<>(DeparturePeriod.class);
        for (DeparturePeriod period : DeparturePeriod.values()) {
            map.put(period, nearest(period, overlapping));
        }

        if (!timeSensitive) {
            collapseTo(map, overlapping.get(0));
            return new PeriodPlan(map, activeOf(map, overlapping));
        }

        int ladderStep = 0;
        while (activeOf(map, overlapping).size() > 1
                && activeOf(map, overlapping).size() * directedPairs > maxPrefetchCalls
                && ladderStep < MERGE_LADDER.length) {
            DeparturePeriod from = MERGE_LADDER[ladderStep][0];
            DeparturePeriod to = MERGE_LADDER[ladderStep][1];
            merge(map, from, nearest(to, overlapping));
            ladderStep++;
        }
        return new PeriodPlan(map, activeOf(map, overlapping));
    }

    private static List<DeparturePeriod> overlapping(TimeWindow window) {
        List<DeparturePeriod> result = new ArrayList<>();
        for (DeparturePeriod period : DeparturePeriod.values()) {
            boolean overlaps = window.getStart().isBefore(period.getEnd())
                    && period.getStart().isBefore(window.getEnd());
            if (overlaps) {
                result.add(period);
            }
        }
        if (result.isEmpty()) {
            result.add(DeparturePeriod.containing(window.getStart()));
        }
        return result;
    }

    private static DeparturePeriod nearest(DeparturePeriod period, List<DeparturePeriod> candidates) {
        if (candidates.contains(period)) {
            return period;
        }
        DeparturePeriod best = candidates.get(0);
        int bestDistance = Math.abs(best.ordinal() - period.ordinal());
        for (DeparturePeriod candidate : candidates) {
            int distance = Math.abs(candidate.ordinal() - period.ordinal());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void merge(Map<DeparturePeriod, DeparturePeriod> map,
                              DeparturePeriod from, DeparturePeriod to) {
        if (from == to) {
            return;
        }
        for (Map.Entry<DeparturePeriod, DeparturePeriod> entry : map.entrySet()) {
            if (entry.getValue() == from) {
                entry.setValue(to);
            }
        }
    }

    private static void collapseTo(Map<DeparturePeriod, DeparturePeriod> map, DeparturePeriod target) {
        for (Map.Entry<DeparturePeriod, DeparturePeriod> entry : map.entrySet()) {
            entry.setValue(target);
        }
    }

    private static List<DeparturePeriod> activeOf(Map<DeparturePeriod, DeparturePeriod> map,
                                                  List<DeparturePeriod> overlapping) {
        Set<DeparturePeriod> distinct = new LinkedHashSet<>();
        for (DeparturePeriod period : overlapping) {
            distinct.add(map.get(period));
        }
        return new ArrayList<>(distinct);
    }

    /** Periods actually prefetched, in chronological order. */
    public List<DeparturePeriod> activePeriods() {
        return Collections.unmodifiableList(active);
    }

    public int size() {
        return active.size();
    }

    /** The active period a departure at {@code time} reads from. Deterministic and total. */
    public DeparturePeriod resolve(LocalTime time) {
        return assignment.get(DeparturePeriod.containing(time));
    }

    /** Prefetch requests this plan will issue for {@code directedPairs} legs. */
    public int prefetchCallCount(int directedPairs) {
        return active.size() * directedPairs;
    }

    /**
     * Whether this plan respects the prefetch ceiling, or has already collapsed to the
     * single irreducible matrix and cannot go lower. See {@link #MAX_PREFETCH_CALLS}.
     */
    public boolean withinPrefetchBudget(int directedPairs) {
        return active.size() == 1 || prefetchCallCount(directedPairs) <= MAX_PREFETCH_CALLS;
    }
}
