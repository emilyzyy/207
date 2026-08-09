package use_case.autoschedule.policy;

import use_case.autoschedule.PlacedActivity;
import use_case.autoschedule.PolicyContext;
import use_case.autoschedule.PolicyId;
import use_case.autoschedule.Reason;
import use_case.autoschedule.ReasonCode;
import entity.valueobjects.IndoorOutdoorType;
import java.time.LocalTime;

/**
 * Prefers to place outdoor activities in daylight.
 *
 * <p>The window is a documented constant rather than a real sunrise/sunset lookup. A
 * sunrise service would add a dependency and a failure mode for a preference that only
 * breaks ties, so the constant is the honest trade: it is approximate, it is soft, and
 * the user can switch it off.</p>
 */
public final class DaylightPolicy implements SoftPolicy {

    /** Approximate daylight for the destinations this project targets. */
    static final LocalTime DAYLIGHT_START = LocalTime.of(8, 0);
    static final LocalTime DAYLIGHT_END = LocalTime.of(19, 0);

    /** Ceiling so a single dusk activity cannot dominate the ranking. */
    public static final int MAX_PENALTY_MINUTES = 90;

    @Override
    public PolicyId id() {
        return PolicyId.DAYLIGHT;
    }

    @Override
    public int penaltyMinutes(PlacedActivity placement, PolicyContext context) {
        if (!isOutdoor(placement)) {
            return 0;
        }
        return Math.min(MAX_PENALTY_MINUTES, minutesOutsideDaylight(placement));
    }

    @Override
    public Reason reasonFor(PlacedActivity placement, PolicyContext context) {
        if (!isOutdoor(placement)) {
            return null;
        }
        String eventId = placement.getTask().getEventId();
        if (minutesOutsideDaylight(placement) == 0) {
            return new Reason(eventId, ReasonCode.IN_DAYLIGHT, "");
        }
        return new Reason(eventId, ReasonCode.OUTSIDE_DAYLIGHT, "");
    }

    private static boolean isOutdoor(PlacedActivity placement) {
        IndoorOutdoorType type = placement.getTask().getActivity().getIndoorOutdoorType();
        return type == IndoorOutdoorType.OUTDOOR || type == IndoorOutdoorType.MIXED;
    }

    /** Minutes of the activity that fall before dawn or after dusk. */
    private static int minutesOutsideDaylight(PlacedActivity placement) {
        int before = overlap(placement.getStart(), placement.getEnd(),
                LocalTime.MIDNIGHT, DAYLIGHT_START);
        int after = overlap(placement.getStart(), placement.getEnd(),
                DAYLIGHT_END, LocalTime.MAX);
        return before + after;
    }

    private static int overlap(LocalTime start, LocalTime end,
                               LocalTime windowStart, LocalTime windowEnd) {
        LocalTime from = start.isAfter(windowStart) ? start : windowStart;
        LocalTime to = end.isBefore(windowEnd) ? end : windowEnd;
        if (!to.isAfter(from)) {
            return 0;
        }
        return (to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }
}
