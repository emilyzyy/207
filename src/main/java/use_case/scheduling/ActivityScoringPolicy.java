package use_case.scheduling;

import entity.entities.Activity;
import entity.valueobjects.WeatherSeverity;

/** Scores a feasible activity relative to the scheduler's current location. */
public interface ActivityScoringPolicy {
    /**
     * Performs the s co re operation.
     * @param travelMinutes the t ra ve lm in ut es value
     * @param weatherSeverity the w ea th er se ve ri ty value
     * @param activity the a ct iv it y value
     * @return the result of the operation
     */
    double score(Activity activity, int travelMinutes, WeatherSeverity weatherSeverity);
}
