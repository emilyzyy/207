package use_case.autoschedule;

import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import entity.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Travel and idle totals for a schedule that already exists.
 *
 * <p>Used for the "before" half of the Preview's comparison. Without it the Preview could
 * only claim the new plan is better; with it the user sees by how much.</p>
 */
public final class ScheduleMetrics {

    private final int travelMinutes;
    private final int idleMinutes;

    private ScheduleMetrics(int travelMinutes, int idleMinutes) {
        this.travelMinutes = travelMinutes;
        this.idleMinutes = idleMinutes;
    }

    /**
     * Totals for a plan that records its own travel as explicit rows.
     *
     * <p>Kept for schedules that already contain travel events, such as one Autoschedule
     * has applied. A hand-built plan has no travel rows at all, and for that
     * {@link #ofExistingSchedule(List, TravelTimeEstimator, TransportationMode, LocalDate)}
     * is the honest measure — see its note.</p>
     */
    public static ScheduleMetrics ofExistingSchedule(List<ScheduledEvent> events) {
        List<ScheduledEvent> sorted = new ArrayList<>(events);
        Collections.sort(sorted, (left, right) -> left.getStartTime().compareTo(right.getStartTime()));

        int travel = 0;
        int idle = 0;
        LocalTime previousEnd = null;
        for (ScheduledEvent event : sorted) {
            if (event.getEventType() == EventType.TRAVEL) {
                travel += minutes(event.getStartTime(), event.getEndTime());
            }
            if (previousEnd != null && event.getStartTime().isAfter(previousEnd)) {
                idle += minutes(previousEnd, event.getStartTime());
            }
            if (previousEnd == null || event.getEndTime().isAfter(previousEnd)) {
                previousEnd = event.getEndTime();
            }
        }
        return new ScheduleMetrics(travel, idle);
    }

    /**
     * Totals for the plan as it really stands, counting the journeys it implies.
     *
     * <p>This exists because the simpler reading was wrong in a way that flattered the
     * feature. Summing only {@code EventType.TRAVEL} rows reports zero travel for a plan
     * the traveller built by hand — those plans have no travel rows — so the Preview
     * compared "travel the user happened to write down" against "travel the scheduler
     * computed", and Autoschedule appeared to invent journeys it had merely made visible.
     * A day with activities in three different places has never cost zero minutes of
     * travel.</p>
     *
     * <p>So consecutive activities with no travel row between them are charged the journey
     * they actually require, using the same estimator the search uses, and the gap they sit
     * in is credited as waiting only for whatever is left over. Explicit travel rows are
     * still trusted where they exist, so a plan Autoschedule already applied is not counted
     * twice.</p>
     *
     * <p>An estimator failure degrades to the explicit-rows reading rather than losing the
     * Preview: a missing comparison is better than no schedule.</p>
     */
    public static ScheduleMetrics ofExistingSchedule(List<ScheduledEvent> events,
                                                     TravelTimeEstimator estimator,
                                                     TransportationMode mode,
                                                     LocalDate date) {
        if (estimator == null || mode == null || date == null) {
            return ofExistingSchedule(events);
        }
        List<ScheduledEvent> sorted = new ArrayList<>(events);
        Collections.sort(sorted,
                (left, right) -> left.getStartTime().compareTo(right.getStartTime()));

        int travel = 0;
        int idle = 0;
        ScheduledEvent previousActivity = null;
        LocalTime previousEnd = null;
        boolean travelSincePreviousActivity = false;

        for (ScheduledEvent event : sorted) {
            if (event.getEventType() == EventType.TRAVEL) {
                travel += minutes(event.getStartTime(), event.getEndTime());
                travelSincePreviousActivity = true;
                if (previousEnd == null || event.getEndTime().isAfter(previousEnd)) {
                    previousEnd = event.getEndTime();
                }
                continue;
            }

            int gap = previousEnd != null && event.getStartTime().isAfter(previousEnd)
                    ? minutes(previousEnd, event.getStartTime()) : 0;

            if (previousActivity != null && !travelSincePreviousActivity) {
                int implied = estimateBetween(estimator, previousActivity, event, mode, date);
                travel += implied;
                idle += Math.max(0, gap - implied);
            } else {
                idle += gap;
            }

            previousActivity = event;
            travelSincePreviousActivity = false;
            if (previousEnd == null || event.getEndTime().isAfter(previousEnd)) {
                previousEnd = event.getEndTime();
            }
        }
        return new ScheduleMetrics(travel, idle);
    }

    private static int estimateBetween(TravelTimeEstimator estimator, ScheduledEvent from,
                                       ScheduledEvent to, TransportationMode mode,
                                       LocalDate date) {
        if (from.getActivity() == null || to.getActivity() == null) {
            return 0;
        }
        try {
            TravelEstimate estimate = estimator.estimate(from.getActivity().getLocation(),
                    to.getActivity().getLocation(), mode,
                    LocalDateTime.of(date, from.getEndTime()));
            return estimate == null ? 0 : Math.max(0, estimate.getMinutes());
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    public int getTravelMinutes() {
        return travelMinutes;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    private static int minutes(LocalTime from, LocalTime to) {
        return (to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }
}
