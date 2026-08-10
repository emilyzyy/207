package use_case.scheduling;

import entity.entities.Activity;
import entity.valueobjects.WeatherSeverity;

/** Scores a feasible activity relative to the scheduler's current location. */
public interface ActivityScoringPolicy {
    double score(Activity activity, int travelMinutes, WeatherSeverity weatherSeverity);
}
