package closeai.application.autoschedule;

import closeai.application.autoschedule.policy.SoftPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The scheduling intelligence in force for one run.
 *
 * <p>Minimising travel, cutting wasted waiting, sensible meal times, daylight for
 * outdoor activities and weather awareness are all built in and always active. They are
 * what the feature is for, and a schedule that ignored them would not be worth
 * previewing, so they are not presented as options to switch off.</p>
 *
 * <p>The single thing the traveller does decide is whether to keep the order they
 * already arranged. That is a genuine matter of taste rather than a question of quality,
 * which is exactly why it is the one setting that is offered.</p>
 */
public final class SchedulingPreferences {

    /** Minutes charged per position an activity is moved from where the user put it. */
    static final int ORDER_PENALTY_PER_POSITION = 2;

    /**
     * Ceiling on the order charge. Keeping it small means preserving the user's order
     * decides between schedules that are otherwise close, and never outweighs a day that
     * is genuinely better.
     */
    static final int MAX_ORDER_PENALTY_MINUTES = 30;

    private static final SchedulingPreferences NONE =
            new SchedulingPreferences(Collections.emptyList(), false, PolicyContext.empty());

    private final List<SoftPolicy> policies;
    private final boolean keepCurrentOrder;
    private final PolicyContext context;

    public SchedulingPreferences(List<SoftPolicy> policies, boolean keepCurrentOrder,
                                 PolicyContext context) {
        this.policies = Collections.unmodifiableList(new ArrayList<>(
                policies == null ? Collections.<SoftPolicy>emptyList() : policies));
        this.keepCurrentOrder = keepCurrentOrder;
        this.context = context == null ? PolicyContext.empty() : context;
    }

    /** Travel and idle only: used by engine tests that isolate the search itself. */
    public static SchedulingPreferences none() {
        return NONE;
    }

    /**
     * The built-in intelligence, plus the traveller's one choice.
     *
     * @param builtInPolicies every registered policy; all of them are always active
     */
    public static SchedulingPreferences builtIn(List<SoftPolicy> builtInPolicies,
                                                boolean keepCurrentOrder,
                                                PolicyContext context) {
        return new SchedulingPreferences(builtInPolicies, keepCurrentOrder, context);
    }

    public List<SoftPolicy> getPolicies() {
        return policies;
    }

    public boolean isKeepCurrentOrder() {
        return keepCurrentOrder;
    }

    public PolicyContext getContext() {
        return context;
    }

    /** The capped charge for moving activities away from the order the user chose. */
    public int orderPenaltyFor(int totalDisplacement) {
        if (!keepCurrentOrder) {
            return 0;
        }
        return Math.min(MAX_ORDER_PENALTY_MINUTES, totalDisplacement * ORDER_PENALTY_PER_POSITION);
    }

    /** What the Preview lists back as the objectives that were applied. */
    public List<PolicyId> activeIds() {
        List<PolicyId> ids = new ArrayList<>();
        for (SoftPolicy policy : policies) {
            ids.add(policy.id());
        }
        ids.add(PolicyId.REDUCE_IDLE);
        if (keepCurrentOrder) {
            ids.add(PolicyId.PRESERVE_ORDER);
        }
        return Collections.unmodifiableList(ids);
    }
}
