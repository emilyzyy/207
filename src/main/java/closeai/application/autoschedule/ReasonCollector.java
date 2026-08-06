package closeai.application.autoschedule;

import closeai.application.autoschedule.policy.SoftPolicy;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Gathers the explanations for a finished schedule.
 *
 * <p>Runs once, on the plan that was actually chosen, rather than during the search
 * where the work would be thrown away for every rejected branch. Structural reasons come
 * from the schedule itself; the rest come from the policies, each of which knows why it
 * was content or unhappy with a placement. Nothing here writes a sentence — that is the
 * Presenter's job, and keeping it there is what stops explanations turning into strings
 * frozen into the use case.</p>
 */
public final class ReasonCollector {

    /** How close to closing time counts as "placed because it closes soon". */
    static final int CLOSING_SOON_MINUTES = 60;

    /**
     * Reasons for every placement, most important first within each activity.
     *
     * @param blockedWindows the user's unavailable periods, to spot placements pushed clear of one
     */
    public List<Reason> collect(SchedulePlan plan, SchedulingPreferences preferences,
                                List<TimeWindow> blockedWindows) {
        List<Reason> reasons = new ArrayList<>();
        for (PlacedActivity placement : plan.getPlacements()) {
            reasons.addAll(structuralReasons(placement, blockedWindows));
            for (SoftPolicy policy : preferences.getPolicies()) {
                Reason reason = policy.reasonFor(placement, preferences.getContext());
                if (reason != null) {
                    reasons.add(reason);
                }
            }
        }
        return reasons;
    }

    private List<Reason> structuralReasons(PlacedActivity placement, List<TimeWindow> blocked) {
        List<Reason> reasons = new ArrayList<>();
        ScheduleTask task = placement.getTask();
        String eventId = task.getEventId();

        if (task.isLocked()) {
            reasons.add(new Reason(eventId, ReasonCode.LOCKED_BY_USER, ""));
            return reasons;
        }

        if (placement.getStart().equals(task.getOpeningTime())
                && placement.getIdleMinutesBefore() > 0) {
            reasons.add(new Reason(eventId, ReasonCode.OPENS_LATER,
                    task.getOpeningTime().toString()));
        }

        int untilClosing = minutes(placement.getEnd(), task.getClosingTime());
        if (untilClosing >= 0 && untilClosing <= CLOSING_SOON_MINUTES) {
            reasons.add(new Reason(eventId, ReasonCode.CLOSING_SOON,
                    task.getClosingTime().toString()));
        }

        if (blocked != null) {
            for (TimeWindow window : blocked) {
                if (placement.getStart().equals(window.getEnd())) {
                    reasons.add(new Reason(eventId, ReasonCode.AVOIDS_UNAVAILABLE_PERIOD,
                            window.toString()));
                    break;
                }
            }
        }
        return reasons;
    }

    private static int minutes(LocalTime from, LocalTime to) {
        return (to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }
}
