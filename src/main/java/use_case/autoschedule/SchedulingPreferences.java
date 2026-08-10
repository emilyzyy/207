package use_case.autoschedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import entity.valueobjects.WeatherOption;
import use_case.autoschedule.policy.SoftPolicy;

/**
 * The scheduling intelligence in force for one run.
 *
 * <p>Minimising travel, cutting wasted waiting, sensible meal times and daylight for
 * outdoor activities are all built in and always active. They are what the feature is
 * for, and a schedule that ignored them would not be worth previewing, so they are not
 * presented as options to switch off.</p>
 *
 * <p>Two things the traveller does decide. Whether to keep the order they already
 * arranged is a genuine matter of taste rather than a question of quality. Whether to
 * consider weather is offered only when the forecast can tell one hour from another,
 * because a whole-day outlook has nothing to say about <em>when</em> to do things; see
 * {@link WeatherOption}. Both are soft and bounded, and neither can make a day
 * unschedulable.</p>
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
    private final boolean countTravel;
    private final boolean countIdle;

    public SchedulingPreferences(List<SoftPolicy> policies, boolean keepCurrentOrder,
                                 PolicyContext context) {
        this(policies, keepCurrentOrder, context, true, true);
    }

    /**
      * @param keepCurrentOrder the k ee pc ur re nt or de r value
      * @param policies the p ol ic ie s value
     * @param countTravel whether travel minutes are part of the cost being minimised
     * @param countIdle   whether avoidable waiting is part of that cost
     */
    public SchedulingPreferences(List<SoftPolicy> policies, boolean keepCurrentOrder,
                                 PolicyContext context, boolean countTravel,
                                 boolean countIdle) {
        this.policies = Collections.unmodifiableList(new ArrayList<>(
                policies == null ? Collections.<SoftPolicy>emptyList() : policies));
        this.keepCurrentOrder = keepCurrentOrder;
        this.context = context == null ? PolicyContext.empty() : context;
        this.countTravel = countTravel;
        this.countIdle = countIdle;
    }

    /**
     * Travel and idle only: used by engine tests that isolate the search itself.
     * @return the result of the operation
     */
    public static SchedulingPreferences none() {
        return NONE;
    }
    /**
     * The built-in intelligence, plus the traveller's one choice.
     *
     * @param builtInPolicies every registered policy; all of them are always active
      * @return the result of the operation
     */

    public static SchedulingPreferences builtIn(List<SoftPolicy> builtInPolicies,
                                                boolean keepCurrentOrder,
                                                PolicyContext context) {
        return new SchedulingPreferences(builtInPolicies, keepCurrentOrder, context);
    }

    /**
     * Performs the b ui lt in operation.
     * @param builtInPolicies the b ui lt in po li ci es value
     * @return the result of the operation
     */
    public static SchedulingPreferences builtIn(List<SoftPolicy> builtInPolicies,
                                                boolean keepCurrentOrder,
                                                PolicyContext context,
                                                boolean countTravel, boolean countIdle) {
        return new SchedulingPreferences(builtInPolicies, keepCurrentOrder, context,
                countTravel, countIdle);
    }

    /**
     * Whether travel minutes count toward the cost being minimised.
     *
     * <p>Feasibility is unaffected either way: the traveller still has to physically reach
     * each place, and the placer still refuses a leg that cannot be made. This only decides
     * whether a shorter journey makes one schedule better than another.</p>
      * @return the result of the operation
     */
    public boolean countsTravel() {
        return countTravel;
    }
    /**
     * Whether avoidable waiting counts toward the cost being minimised.
     * @return the result of the operation
     */

    public boolean countsIdle() {
        return countIdle;
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
    /**
     * The capped charge for moving activities away from the order the user chose.
     * @param totalDisplacement the t ot al di sp la ce me nt value
     * @return the result of the operation
     */

    public int orderPenaltyFor(int totalDisplacement) {
        if (!keepCurrentOrder) {
            return 0;
        }
        return Math.min(MAX_ORDER_PENALTY_MINUTES, totalDisplacement * ORDER_PENALTY_PER_POSITION);
    }

    /**
     * What the Preview lists back as the objectives that were applied.
     *
     * <p>Weather earns its place here only when it could actually change something. It is
     * omitted when the traveller left it unticked, and omitted just the same when they
     * ticked it but the forecast turned out to cover the whole day: in both cases it
     * scored nothing, and listing an objective that contributed zero would tell the user
     * their day was arranged around something it was not.</p>
      * @return the result of the operation
     */
    public List<PolicyId> activeIds() {
        final List<PolicyId> ids = new ArrayList<>();
        for (SoftPolicy policy : policies) {
            if (policy.id() == PolicyId.WEATHER && !context.getWeather().canDistinguishTimes()) {
                continue;
            }
            ids.add(policy.id());
        }
        if (countIdle) {
            ids.add(PolicyId.REDUCE_IDLE);
        }
        if (keepCurrentOrder) {
            ids.add(PolicyId.PRESERVE_ORDER);
        }
        return Collections.unmodifiableList(ids);
    }
}
