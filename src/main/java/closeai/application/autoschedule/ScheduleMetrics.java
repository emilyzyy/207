package closeai.application.autoschedule;

import closeai.domain.entities.ScheduledEvent;
import closeai.domain.valueobjects.EventType;
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
