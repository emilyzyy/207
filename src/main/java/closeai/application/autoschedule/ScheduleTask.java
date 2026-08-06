package closeai.application.autoschedule;

import closeai.domain.entities.Activity;
import java.time.LocalTime;

/**
 * One Day Plan activity as the scheduler sees it: an identity, a duration that must
 * be preserved, the venue's opening window, and whether the user pinned it.
 *
 * <p>The duration comes from the existing event (end minus start), never from the
 * activity's estimate, so a manual duration edit survives Autoschedule.</p>
 */
public final class ScheduleTask {
    private final String eventId;
    private final Activity activity;
    private final int durationMinutes;
    private final int originalIndex;
    private final TimeWindow lockedAt;

    public ScheduleTask(String eventId, Activity activity, int durationMinutes,
                        int originalIndex, TimeWindow lockedAt) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("Task event id is required");
        }
        if (activity == null) {
            throw new IllegalArgumentException("Task activity is required");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Task duration must be positive");
        }
        if (originalIndex < 0) {
            throw new IllegalArgumentException("Original index cannot be negative");
        }
        if (lockedAt != null && lockedAt.durationMinutes() != durationMinutes) {
            throw new IllegalArgumentException(
                    "Locked window must match the activity duration for " + eventId);
        }
        this.eventId = eventId;
        this.activity = activity;
        this.durationMinutes = durationMinutes;
        this.originalIndex = originalIndex;
        this.lockedAt = lockedAt;
    }

    public static ScheduleTask movable(String eventId, Activity activity,
                                       int durationMinutes, int originalIndex) {
        return new ScheduleTask(eventId, activity, durationMinutes, originalIndex, null);
    }

    public String getEventId() {
        return eventId;
    }

    public Activity getActivity() {
        return activity;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getOriginalIndex() {
        return originalIndex;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public TimeWindow getLockedAt() {
        return lockedAt;
    }

    public LocalTime getOpeningTime() {
        return activity.getOpeningTime();
    }

    public LocalTime getClosingTime() {
        return activity.getClosingTime();
    }

    @Override
    public String toString() {
        return eventId;
    }
}
