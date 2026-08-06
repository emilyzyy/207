package closeai.application.autoschedule;

/**
 * Why no complete valid schedule exists. Expected infeasibility is a result the user
 * reads, never an exception, and it always names the constraint that bound.
 */
public final class ScheduleConflict {
    private final String blockingEventId;
    private final String reason;

    public ScheduleConflict(String blockingEventId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Conflict reason is required");
        }
        this.blockingEventId = blockingEventId == null ? "" : blockingEventId;
        this.reason = reason;
    }

    public String getBlockingEventId() {
        return blockingEventId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "conflict(" + blockingEventId + "): " + reason;
    }
}
