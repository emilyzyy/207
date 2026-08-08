package trippy.application.autoschedule;

import java.util.Objects;

/**
 * Why no complete valid schedule exists.
 *
 * <p>A day that cannot be arranged is an ordinary outcome, not a programming error, so
 * it travels outward as data rather than as an exception. The kind and the named subject
 * let the Presenter write a specific sentence — which activity, which lock, which window
 * — instead of a generic apology, and they keep that wording out of the engine.</p>
 */
public final class ScheduleConflict {

    /** What went wrong, so the Presenter can phrase it without parsing text. */
    public enum Kind {
        /** One activity cannot fit its own opening hours and the availability window. */
        ACTIVITY_CANNOT_FIT,
        /** No ordering of the day satisfies every constraint once travel is included. */
        NO_FEASIBLE_ORDER,
        /** A locked activity sits outside the availability window. */
        LOCK_OUTSIDE_AVAILABILITY,
        /** A locked activity falls outside its venue's opening hours. */
        LOCK_OUTSIDE_OPENING_HOURS,
        /** Two locked activities overlap each other. */
        LOCKS_OVERLAP,
        /** A locked activity overlaps a period the user marked unavailable. */
        LOCK_INSIDE_UNAVAILABLE_PERIOD,
        /** A locked event id no longer matches anything in the Day Plan. */
        LOCK_NOT_IN_PLAN,
        /** Travel at the real departure times left no valid arrangement. */
        REFINED_TRAVEL_INFEASIBLE
    }

    private final Kind kind;
    private final String blockingEventId;
    private final String subject;
    private final int requiredMinutes;
    private final int availableMinutes;

    private ScheduleConflict(Kind kind, String blockingEventId, String subject,
                             int requiredMinutes, int availableMinutes) {
        this.kind = kind;
        this.blockingEventId = blockingEventId == null ? "" : blockingEventId;
        this.subject = subject == null ? "" : subject;
        this.requiredMinutes = requiredMinutes;
        this.availableMinutes = availableMinutes;
    }

    public static ScheduleConflict activityCannotFit(String eventId, String activityName,
                                                     int requiredMinutes, int availableMinutes) {
        return new ScheduleConflict(Kind.ACTIVITY_CANNOT_FIT, eventId, activityName,
                requiredMinutes, availableMinutes);
    }

    public static ScheduleConflict noFeasibleOrder() {
        return new ScheduleConflict(Kind.NO_FEASIBLE_ORDER, "", "", 0, 0);
    }

    public static ScheduleConflict refinedTravelInfeasible() {
        return new ScheduleConflict(Kind.REFINED_TRAVEL_INFEASIBLE, "", "", 0, 0);
    }

    public static ScheduleConflict of(Kind kind, String eventId, String subject) {
        return new ScheduleConflict(kind, eventId, subject, 0, 0);
    }

    public Kind getKind() {
        return kind;
    }

    /** The event the user should look at, or empty when the whole day is the problem. */
    public String getBlockingEventId() {
        return blockingEventId;
    }

    /** Supporting name: the activity, or the window that clashed. */
    public String getSubject() {
        return subject;
    }

    public int getRequiredMinutes() {
        return requiredMinutes;
    }

    public int getAvailableMinutes() {
        return availableMinutes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScheduleConflict)) {
            return false;
        }
        ScheduleConflict that = (ScheduleConflict) other;
        return kind == that.kind && blockingEventId.equals(that.blockingEventId)
                && subject.equals(that.subject) && requiredMinutes == that.requiredMinutes
                && availableMinutes == that.availableMinutes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, blockingEventId, subject, requiredMinutes, availableMinutes);
    }

    @Override
    public String toString() {
        return kind + "(" + blockingEventId + ")";
    }
}
