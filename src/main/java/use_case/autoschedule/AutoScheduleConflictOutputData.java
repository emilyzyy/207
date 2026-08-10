package use_case.autoschedule;

/**
 * Why the day could not be arranged.
 *
 * <p>Structured rather than pre-written prose: the Presenter decides the wording, and
 * tests can assert on the kind without matching strings. The original Day Plan is
 * always untouched when this is produced.</p>
 */
public final class AutoScheduleConflictOutputData {

    private final ScheduleConflict.Kind kind;
    private final String blockingEventId;
    private final String subject;
    private final int requiredMinutes;
    private final int availableMinutes;
    private final String detail;
    private final TimeWindow lockedWindow;
    private final TimeWindow unavailableWindow;

    public AutoScheduleConflictOutputData(ScheduleConflict conflict) {
        this.kind = conflict.getKind();
        this.blockingEventId = conflict.getBlockingEventId();
        this.subject = conflict.getSubject();
        this.requiredMinutes = conflict.getRequiredMinutes();
        this.availableMinutes = conflict.getAvailableMinutes();
        this.detail = conflict.getDetail();
        this.lockedWindow = conflict.getLockedWindow();
        this.unavailableWindow = conflict.getUnavailableWindow();
    }

    public ScheduleConflict.Kind getKind() {
        return kind;
    }

    public String getBlockingEventId() {
        return blockingEventId;
    }

    public String getSubject() {
        return subject;
    }

    /** Extra wording the message needs, such as the weekday a venue is shut. */
    public String getDetail() {
        return detail;
    }

    public int getRequiredMinutes() {
        return requiredMinutes;
    }

    public int getAvailableMinutes() {
        return availableMinutes;
    }

    public TimeWindow getLockedWindow() {
        return lockedWindow;
    }

    public TimeWindow getUnavailableWindow() {
        return unavailableWindow;
    }
}
