package trippy.application.scheduling;

import trippy.domain.entities.Activity;
import trippy.domain.valueobjects.WeatherSeverity;

/** Scores a feasible activity relative to the scheduler's current location. */
public interface ActivityScoringPolicy {
    double score(Activity activity, int travelMinutes, WeatherSeverity weatherSeverity);
}
