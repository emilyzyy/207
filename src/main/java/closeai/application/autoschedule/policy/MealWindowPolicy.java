package closeai.application.autoschedule.policy;

import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.PolicyContext;
import closeai.application.autoschedule.PolicyId;
import closeai.application.autoschedule.Reason;
import closeai.application.autoschedule.ReasonCode;
import closeai.domain.valueobjects.ActivityCategory;
import java.time.LocalTime;

/**
 * Prefers to place meals at customary eating times.
 *
 * <p>The windows below are conventions, not facts: when people eat varies by culture and
 * by person. That is exactly why this is a soft preference the user can switch off, and
 * why it can never override opening hours or any other hard rule. A restaurant will
 * still be scheduled at an odd hour if that is the only time it fits; it simply scores
 * worse than a schedule that manages lunch at lunchtime.</p>
 */
public final class MealWindowPolicy implements SoftPolicy {

    /** Customary lunch window. */
    static final LocalTime LUNCH_START = LocalTime.of(11, 30);
    static final LocalTime LUNCH_END = LocalTime.of(14, 0);
    /** Customary dinner window. */
    static final LocalTime DINNER_START = LocalTime.of(17, 30);
    static final LocalTime DINNER_END = LocalTime.of(21, 0);

    /** Ceiling so one badly-timed meal cannot outweigh every other consideration. */
    static final int MAX_PENALTY_MINUTES = 120;

    @Override
    public PolicyId id() {
        return PolicyId.MEAL_TIME;
    }

    @Override
    public int penaltyMinutes(PlacedActivity placement, PolicyContext context) {
        if (placement.getTask().getActivity().getCategory() != ActivityCategory.FOOD) {
            return 0;
        }
        return Math.min(MAX_PENALTY_MINUTES, minutesFromNearestMealWindow(placement.getStart()));
    }

    @Override
    public Reason reasonFor(PlacedActivity placement, PolicyContext context) {
        if (placement.getTask().getActivity().getCategory() != ActivityCategory.FOOD) {
            return null;
        }
        String eventId = placement.getTask().getEventId();
        if (minutesFromNearestMealWindow(placement.getStart()) == 0) {
            return new Reason(eventId, ReasonCode.IN_MEAL_WINDOW, "");
        }
        return new Reason(eventId, ReasonCode.OUTSIDE_MEAL_WINDOW, "");
    }

    private static int minutesFromNearestMealWindow(LocalTime start) {
        int lunch = distanceTo(start, LUNCH_START, LUNCH_END);
        int dinner = distanceTo(start, DINNER_START, DINNER_END);
        return Math.min(lunch, dinner);
    }

    private static int distanceTo(LocalTime time, LocalTime windowStart, LocalTime windowEnd) {
        if (!time.isBefore(windowStart) && !time.isAfter(windowEnd)) {
            return 0;
        }
        if (time.isBefore(windowStart)) {
            return minutes(time, windowStart);
        }
        return minutes(windowEnd, time);
    }

    private static int minutes(LocalTime from, LocalTime to) {
        return Math.abs(to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }
}
