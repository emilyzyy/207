package closeai.application.scheduling;

import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.WeatherSeverity;

/** Scores a feasible activity relative to the scheduler's current location. */
public interface ActivityScoringPolicy {
    double score(Activity activity, int travelMinutes, WeatherSeverity weatherSeverity);
}
