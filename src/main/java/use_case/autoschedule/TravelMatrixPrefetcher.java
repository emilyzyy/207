package use_case.autoschedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import entity.valueobjects.TransportationMode;

/**
 * Builds the bucketed {@link TravelMatrix} for one run by asking the estimator for
 * every directed leg in every active departure period.
 *
 * <p>This is the only component that fans out travel requests. Doing it here, before
 * the search starts, is what keeps the recursion free of network calls and therefore
 * deterministic and offline-testable.</p>
 */
public final class TravelMatrixPrefetcher {

    private final TravelTimeEstimator estimator;

    public TravelMatrixPrefetcher(TravelTimeEstimator estimator) {
        if (estimator == null) {
            throw new IllegalArgumentException("Travel time estimator is required");
        }
        this.estimator = estimator;
    }

    /**
     * Prefetches travel for every ordered pair of {@code tasks}.
     *
     * @param date         the trip date, bound to each departure time
     * @param availability the run's availability window
     * @param tasks        activities to be scheduled, in any order
     * @param mode         transportation mode chosen for this run
      * @return the result of the operation
     */
    public TravelMatrix prefetch(List<ScheduleTask> tasks, TransportationMode mode,
                                 LocalDate date, TimeWindow availability) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Travel prefetch needs at least one activity");
        }
        final int directedPairs = tasks.size() * (tasks.size() - 1);
        final PeriodPlan periods = PeriodPlan.forRun(availability,
                estimator.isTimeSensitive(mode), directedPairs);

        final TravelMatrix.Builder builder = TravelMatrix.builder(periods);
        for (DeparturePeriod period : periods.activePeriods()) {
            final LocalTime departureTime = period.sampleWithin(availability);
            final LocalDateTime departure = LocalDateTime.of(date, departureTime);
            for (ScheduleTask from : tasks) {
                for (ScheduleTask to : tasks) {
                    if (from.getEventId().equals(to.getEventId())) {
                        continue;
                    }
                    final TravelEstimate estimate = estimator.estimate(
                            from.getActivity().getLocation(),
                            to.getActivity().getLocation(),
                            mode, departure);
                    builder.put(from.getEventId(), to.getEventId(), period, estimate);
                }
            }
        }
        return builder.build();
    }
}
