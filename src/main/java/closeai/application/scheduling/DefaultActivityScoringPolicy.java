package closeai.application.scheduling;

import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.WeatherSeverity;

/** Default deterministic balance of quality, travel cost, and weather exposure. */
public final class DefaultActivityScoringPolicy implements ActivityScoringPolicy {
    private static final double RATING_WEIGHT = 2.0;
    private static final double TRAVEL_MINUTE_WEIGHT = 0.05;

    @Override
    public double score(Activity activity, int travelMinutes, WeatherSeverity weatherSeverity) {
        if (activity == null || weatherSeverity == null) {
            throw new IllegalArgumentException("Activity and weather severity are required");
        }
        if (travelMinutes < 0) {
            throw new IllegalArgumentException("Travel minutes cannot be negative");
        }
        return activity.getRating() * RATING_WEIGHT
                - travelMinutes * TRAVEL_MINUTE_WEIGHT
                - severityPenalty(weatherSeverity) * exposureMultiplier(activity.getIndoorOutdoorType());
    }

    private double severityPenalty(WeatherSeverity severity) {
        if (severity == WeatherSeverity.HIGH) return 4.0;
        if (severity == WeatherSeverity.MEDIUM) return 2.0;
        return 0.4;
    }

    private double exposureMultiplier(IndoorOutdoorType type) {
        if (type == IndoorOutdoorType.OUTDOOR) return 1.0;
        if (type == IndoorOutdoorType.MIXED) return 0.5;
        return 0.0;
    }
}
