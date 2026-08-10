package use_case.autoschedule;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import use_case.autoschedule.policy.SoftPolicy;

/**
 * Works out what the proposed day actually improved, by comparing it with the day it
 * replaces.
 *
 * <p>The comparison is the entire value of this class. Everything the schedule already
 * reports describes the <em>result</em> — this activity is in daylight, that one is in a
 * meal window — and a result says nothing about whether scheduling helped. An activity that
 * was already in daylight at 10 a.m. and is still in daylight at 2 p.m. has not been
 * improved, and claiming otherwise is the kind of small dishonesty that makes a user stop
 * believing the rest of the screen.</p>
 *
 * <p>So each per-activity judgement re-runs the <em>same policy objects the search used</em>
 * against the activity's original time, and compares that penalty with the penalty at its
 * proposed time. A lower penalty is a real improvement, measured by the same rule that
 * produced the schedule. Reimplementing "what counts as daylight" here would have been a
 * second definition free to drift from the first.</p>
 *
 * <p>Only improvements are returned. Trade-offs — travel that grew, waiting that grew, an
 * activity pushed out of daylight — are deliberately absent, because these become positive
 * cards; the honest whole picture lives in the before/after figures and the reasons under
 * "Why this schedule?".</p>
 */
public final class ScheduleImprovementFinder {

    /**
     * @param before         totals for the current plan
     * @param afterTravel    total travel in the proposal
     * @param afterIdle      avoidable waiting in the proposal
     * @param originalEvents the day as it stands, activities only
     * @param plan           the proposed day
     * @param preferences    the policies and context the search actually ran with
      * @return the result of the operation
     */
    public List<ScheduleImprovement> find(List<ScheduledEvent> originalEvents,
                                          SchedulePlan plan,
                                          SchedulingPreferences preferences,
                                          ScheduleMetrics before,
                                          int afterTravel,
                                          int afterIdle) {
        final List<ScheduleImprovement> improvements = new ArrayList<>();
        if (plan == null || originalEvents == null) {
            return Collections.unmodifiableList(improvements);
        }

        if (before != null) {
            final int waitingSaved = before.getIdleMinutes() - afterIdle;
            if (waitingSaved > 0) {
                improvements.add(ScheduleImprovement.of(
                        ScheduleImprovementType.WAITING_REDUCED, waitingSaved));
            }
            final int travelSaved = before.getTravelMinutes() - afterTravel;
            if (travelSaved > 0) {
                improvements.add(ScheduleImprovement.of(
                        ScheduleImprovementType.TRAVEL_REDUCED, travelSaved));
            }
        }

        final Map<String, ScheduledEvent> originalById = new HashMap<>();
        for (ScheduledEvent event : originalEvents) {
            if (event.getEventType() == EventType.ACTIVITY && event.getActivity() != null) {
                originalById.put(event.getId(), event);
            }
        }

        final PolicyContext context = preferences == null
                ? PolicyContext.empty() : preferences.getContext();
        final List<SoftPolicy> policies = preferences == null
                ? Collections.<SoftPolicy>emptyList() : preferences.getPolicies();

        for (PlacedActivity placed : plan.getPlacements()) {
            final ScheduleTask task = placed.getTask();
            final ScheduledEvent original = originalById.get(task.getEventId());
            if (original == null) {
                continue;
            }
            final String name = task.getActivity().getName();

            // A pin honoured is an improvement the traveller asked for by hand, and the only
            // one that does not need a policy to notice.
            if (task.isLocked() && placed.getStart().equals(original.getStartTime())) {
                improvements.add(ScheduleImprovement.forActivity(
                        ScheduleImprovementType.LOCK_PRESERVED, name));
            }

            final PlacedActivity asItWas = at(task, original.getStartTime());
            for (SoftPolicy policy : policies) {
                final int penaltyBefore = policy.penaltyMinutes(asItWas, context);
                final int penaltyAfter = policy.penaltyMinutes(placed, context);
                if (penaltyAfter >= penaltyBefore) {
                    continue;
                }
                final ScheduleImprovementType type = typeOf(policy.id());
                if (type != null) {
                    improvements.add(ScheduleImprovement.forActivity(
                            type, penaltyBefore - penaltyAfter, name));
                }
            }
        }

        if (orderPreserved(originalEvents, plan)) {
            improvements.add(ScheduleImprovement.of(
                    ScheduleImprovementType.ORDER_PRESERVED, 0));
        }
        return Collections.unmodifiableList(improvements);
    }

    /**
     * The same task judged as if it had stayed where it was, so policies can compare.
     * @param start the s ta rt value
     * @param task the t as k value
     * @return the result of the operation
     */
    private static PlacedActivity at(ScheduleTask task, LocalTime start) {
        return PlacedActivity.first(task, start,
                start.plusMinutes(task.getDurationMinutes()), 0, 0);
    }

    private static ScheduleImprovementType typeOf(PolicyId id) {
        if (id == PolicyId.DAYLIGHT) {
            return ScheduleImprovementType.MOVED_INTO_DAYLIGHT;
        }
        if (id == PolicyId.WEATHER) {
            return ScheduleImprovementType.MOVED_TO_BETTER_WEATHER;
        }
        if (id == PolicyId.MEAL_TIME) {
            return ScheduleImprovementType.MEAL_MOVED_TOWARD_WINDOW;
        }
        return null;
    }

    /**
     * Whether the activities still run in the sequence they were given in.
     *
     * <p>Compares the actual orders, not the traveller's preference. The preference says
     * what was asked for; a schedule can be asked to preserve order and reorder anyway when
     * the day is otherwise much better, and reporting the request as an outcome would be a
     * claim the schedule never earned.</p>
      * @param originalEvents the o ri gi na le ve nt s value
      * @return the result of the operation
     */
    private static boolean orderPreserved(List<ScheduledEvent> originalEvents,
                                          SchedulePlan plan) {
        final List<String> beforeOrder = new ArrayList<>();
        final List<ScheduledEvent> sorted = new ArrayList<>(originalEvents);
        Collections.sort(sorted,
                (left, right) -> left.getStartTime().compareTo(right.getStartTime()));
        for (ScheduledEvent event : sorted) {
            if (event.getEventType() == EventType.ACTIVITY && event.getActivity() != null) {
                beforeOrder.add(event.getId());
            }
        }

        final List<String> afterOrder = new ArrayList<>();
        for (PlacedActivity placed : plan.getPlacements()) {
            afterOrder.add(placed.getTask().getEventId());
        }
        // One activity cannot be out of order with itself, and reporting an unchanged
        // single-item day as an achievement would be noise.
        return beforeOrder.size() > 1 && beforeOrder.equals(afterOrder);
    }
}
