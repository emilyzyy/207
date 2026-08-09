package use_case.autoschedule.policy;

import use_case.autoschedule.PlacedActivity;
import use_case.autoschedule.PolicyContext;
import use_case.autoschedule.PolicyId;
import use_case.autoschedule.Reason;
import use_case.autoschedule.ReasonCode;
import use_case.autoschedule.WeatherContext;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.WeatherSeverity;

/**
 * Prefers to keep exposed activities out of poor weather.
 *
 * <p>Weather is soft here, never hard, and that is a deliberate product decision. This
 * feature never drops an activity, so a hard weather rule could turn a forecast into a
 * day with no schedule at all. Rescheduling cannot change the weather either — it only
 * shifts exposure — so the call belongs to the traveller. The schedule avoids exposure
 * where it can, warns where it cannot, and lets the user decide.</p>
 *
 * <p>With one severity for the whole trip this mostly warns rather than moves, because
 * every candidate time scores alike. Given an hourly forecast the same class starts
 * genuinely relocating outdoor activities, with no change to the engine.</p>
 */
public final class WeatherSuitabilityPolicy implements SoftPolicy {

    /** Equivalent wasted minutes charged per hour of full outdoor exposure. */
    static final int LOW_PENALTY_PER_HOUR = 5;
    static final int MEDIUM_PENALTY_PER_HOUR = 15;
    static final int HIGH_PENALTY_PER_HOUR = 30;

    /** Ceiling so avoiding bad weather can never justify an unreasonable detour. */
    public static final int MAX_PENALTY_MINUTES = 60;

    @Override
    public PolicyId id() {
        return PolicyId.WEATHER;
    }

    @Override
    public int penaltyMinutes(PlacedActivity placement, PolicyContext context) {
        WeatherContext weather = context.getWeather();
        // A forecast covering the whole trip scores every candidate time alike, so it
        // cannot inform when to do anything. Charging for it would add a constant to
        // every schedule and imply the timing was weather-optimised when it was not.
        if (!weather.canDistinguishTimes()) {
            return 0;
        }
        double exposure = exposureFactor(placement);
        if (exposure == 0.0) {
            return 0;
        }
        WeatherSeverity severity = weather.severityAt(placement.getStart());
        if (severity == null) {
            return 0;
        }
        int durationMinutes = placement.getTask().getDurationMinutes();
        double hours = durationMinutes / 60.0;
        int raw = (int) Math.round(penaltyPerHour(severity) * hours * exposure);
        return Math.min(MAX_PENALTY_MINUTES, raw);
    }

    @Override
    public Reason reasonFor(PlacedActivity placement, PolicyContext context) {
        if (penaltyMinutes(placement, context) <= 0) {
            return null;
        }
        WeatherSeverity severity = context.getWeather().severityAt(placement.getStart());
        // A non-zero penalty is not the same as bad weather. Every outdoor activity is
        // charged LOW_PENALTY_PER_HOUR even in sunshine, which is deliberate and harmless
        // to the ranking -- it is a constant across candidate times. It is not a reason to
        // show anyone. Saying "poorer weather expected" over a clear forecast was worse
        // than saying nothing, and could appear on the very row that had just earned
        // "Moved to better weather".
        if (severity != WeatherSeverity.MEDIUM && severity != WeatherSeverity.HIGH) {
            return null;
        }
        return new Reason(placement.getTask().getEventId(), ReasonCode.WEATHER_EXPOSURE,
                severity.name());
    }

    /** How exposed the activity is: fully outdoors, partly, or not at all. */
    private static double exposureFactor(PlacedActivity placement) {
        IndoorOutdoorType type = placement.getTask().getActivity().getIndoorOutdoorType();
        if (type == IndoorOutdoorType.OUTDOOR) {
            return 1.0;
        }
        if (type == IndoorOutdoorType.MIXED) {
            return 0.5;
        }
        return 0.0;
    }

    private static int penaltyPerHour(WeatherSeverity severity) {
        if (severity == WeatherSeverity.HIGH) {
            return HIGH_PENALTY_PER_HOUR;
        }
        if (severity == WeatherSeverity.MEDIUM) {
            return MEDIUM_PENALTY_PER_HOUR;
        }
        return LOW_PENALTY_PER_HOUR;
    }
}
