package use_case.autoschedule.policy;

import use_case.autoschedule.PlacedActivity;
import use_case.autoschedule.PolicyContext;
import use_case.autoschedule.PolicyId;
import use_case.autoschedule.Reason;

/**
 * One soft consideration used to rank otherwise valid schedules.
 *
 * <p>Every policy is a separate class registered with the engine, so supporting a new
 * preference means writing a class and registering it rather than editing the search.
 * The engine scores whichever list it is handed and contains no policy-specific
 * branching, which is what makes that claim verifiable rather than aspirational.</p>
 */
public interface SoftPolicy {

    /** Identifies this policy so the user's toggles can select it. */
    PolicyId id();

    /**
     * Cost of this placement, expressed in "equivalent wasted minutes".
     *
     * <p>Every policy uses that same unit so no policy can dominate the ranking merely
     * by choosing a bigger scale. Zero means the policy is content.</p>
     */
    int penaltyMinutes(PlacedActivity placement, PolicyContext context);

    /**
     * Why this policy would explain the placement, or null when it has nothing to say.
     * Called only for the schedule finally chosen, never inside the search.
     */
    Reason reasonFor(PlacedActivity placement, PolicyContext context);
}
