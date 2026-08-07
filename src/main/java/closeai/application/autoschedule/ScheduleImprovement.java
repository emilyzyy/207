package closeai.application.autoschedule;

/**
 * One proven, positively framed thing this schedule achieved.
 *
 * <p>Produced only where the use case can compare the original placement with the proposed
 * one. Nothing here is inferred from wording, and nothing is inferred from the final state
 * alone — see {@link ScheduleImprovementType}.</p>
 *
 * <p>Carries data rather than a sentence. The Presenter turns it into words, for the same
 * reason reason codes do: prose assembled inside the use case is prose the display layer
 * cannot change.</p>
 */
public final class ScheduleImprovement {

    private final ScheduleImprovementType type;
    private final int amount;
    private final String subject;

    private ScheduleImprovement(ScheduleImprovementType type, int amount, String subject) {
        this.type = type;
        this.amount = amount;
        this.subject = subject == null ? "" : subject;
    }

    /** A whole-schedule improvement measured in minutes or activities, e.g. waiting removed. */
    public static ScheduleImprovement of(ScheduleImprovementType type, int amount) {
        return new ScheduleImprovement(type, amount, "");
    }

    /** An improvement attributable to one named activity. */
    public static ScheduleImprovement forActivity(ScheduleImprovementType type, String subject) {
        return new ScheduleImprovement(type, 0, subject);
    }

    /** An improvement attributable to one named activity, with a size. */
    public static ScheduleImprovement forActivity(ScheduleImprovementType type, int amount,
                                                  String subject) {
        return new ScheduleImprovement(type, amount, subject);
    }

    public ScheduleImprovementType getType() {
        return type;
    }

    /** Minutes or a count, depending on the type; zero when the type carries no size. */
    public int getAmount() {
        return amount;
    }

    /** The activity this is about, or empty when it describes the whole day. */
    public String getSubject() {
        return subject;
    }
}
