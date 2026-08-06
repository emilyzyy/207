package closeai.application.autoschedule;

import closeai.application.autoschedule.policy.SoftPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The soft considerations active for one run.
 *
 * <p>The Interactor filters the registered policies down to the ones the user left
 * switched on and hands the result here, so the engine simply scores whatever list it
 * receives. Disabling a preference removes its object from the list rather than setting
 * a flag the search has to test, which is why the search contains no policy-specific
 * branching at all.</p>
 */
public final class SchedulingPreferences {

    private static final SchedulingPreferences NONE = new SchedulingPreferences(
            Collections.emptyList(), false, false, PolicyContext.empty());

    private final List<SoftPolicy> policies;
    private final boolean reduceIdle;
    private final boolean preserveOrder;
    private final PolicyContext context;

    public SchedulingPreferences(List<SoftPolicy> policies, boolean reduceIdle,
                                 boolean preserveOrder, PolicyContext context) {
        this.policies = Collections.unmodifiableList(new ArrayList<>(
                policies == null ? Collections.<SoftPolicy>emptyList() : policies));
        this.reduceIdle = reduceIdle;
        this.preserveOrder = preserveOrder;
        this.context = context == null ? PolicyContext.empty() : context;
    }

    /** Travel minimisation only: what remains when every optional preference is off. */
    public static SchedulingPreferences none() {
        return NONE;
    }

    /**
     * Selects the enabled subset of {@code registered} and the two score tiers.
     *
     * @param enabled ids the user left switched on
     */
    public static SchedulingPreferences select(List<SoftPolicy> registered, Set<PolicyId> enabled,
                                               PolicyContext context) {
        Set<PolicyId> active = enabled == null
                ? Collections.<PolicyId>emptySet() : new LinkedHashSet<>(enabled);
        List<SoftPolicy> chosen = new ArrayList<>();
        if (registered != null) {
            for (SoftPolicy policy : registered) {
                if (active.contains(policy.id())) {
                    chosen.add(policy);
                }
            }
        }
        return new SchedulingPreferences(chosen, active.contains(PolicyId.REDUCE_IDLE),
                active.contains(PolicyId.PRESERVE_ORDER), context);
    }

    public List<SoftPolicy> getPolicies() {
        return policies;
    }

    public boolean isReduceIdleEnabled() {
        return reduceIdle;
    }

    public boolean isPreserveOrderEnabled() {
        return preserveOrder;
    }

    public PolicyContext getContext() {
        return context;
    }

    /** The ids actually in force, for the Preview to list back to the user. */
    public List<PolicyId> activeIds() {
        List<PolicyId> ids = new ArrayList<>();
        for (SoftPolicy policy : policies) {
            ids.add(policy.id());
        }
        if (reduceIdle) {
            ids.add(PolicyId.REDUCE_IDLE);
        }
        if (preserveOrder) {
            ids.add(PolicyId.PRESERVE_ORDER);
        }
        return Collections.unmodifiableList(ids);
    }
}
