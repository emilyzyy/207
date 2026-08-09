package trippy.application.autoschedule;

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

    public AutoScheduleConflictOutputData(ScheduleConflict conflict) {
        this.kind = conflict.getKind();
        this.blockingEventId = conflict.getBlockingEventId();
        this.subject = conflict.getSubject();
        this.requiredMinutes = conflict.getRequiredMinutes();
        this.availableMinutes = conflict.getAvailableMinutes();
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

    public int getRequiredMinutes() {
        return requiredMinutes;
    }

    public int getAvailableMinutes() {
        return availableMinutes;
    }
}
