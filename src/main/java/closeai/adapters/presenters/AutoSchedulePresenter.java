package closeai.adapters.presenters;

import closeai.adapters.viewmodels.AutoScheduleStatus;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.PreviewMetricsView;
import closeai.adapters.viewmodels.ImprovementView;
import closeai.adapters.viewmodels.PreviewRowView;
import closeai.application.autoschedule.AutoScheduleAppliedOutputData;
import closeai.application.autoschedule.AutoScheduleConflictOutputData;
import closeai.application.autoschedule.AutoScheduleOutputBoundary;
import closeai.application.autoschedule.AutoSchedulePreviewOutputData;
import closeai.application.autoschedule.PolicyId;
import closeai.application.autoschedule.ProposedEventData;
import closeai.application.autoschedule.ScheduleImprovement;
import closeai.application.autoschedule.Reason;
import closeai.application.autoschedule.ReasonCode;
import closeai.application.autoschedule.ScheduleConflict;
import closeai.application.autoschedule.TravelEstimateQuality;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.valueobjects.EventType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the use case's answers into something the Day Plan can display.
 *
 * <p>All user-facing wording lives here. The engine and the Interactor deal in reason
 * codes and conflict kinds, which means the language can be reworded, softened or
 * translated without touching a single scheduling decision — and it keeps sentences out
 * of the layers least able to change them.</p>
 *
 * <p>The presenter never lets a proposal masquerade as the itinerary: a Preview updates
 * the preview rows and leaves the real events alone, so the Calendar keeps showing what
 * the traveller has actually agreed to until they apply.</p>
 */
public final class AutoSchedulePresenter implements AutoScheduleOutputBoundary {

    /**
     * Which explanation to show when a row has several. Constraints the traveller cannot
     * change come first, because "it closes at 17:00" answers the question better than
     * "this is a good time for lunch".
     */
    private static final List<ReasonCode> REASON_PRIORITY = Arrays.asList(
            ReasonCode.LOCKED_BY_USER,
            ReasonCode.AVOIDS_UNAVAILABLE_PERIOD,
            ReasonCode.CLOSING_SOON,
            ReasonCode.OPENS_LATER,
            ReasonCode.WEATHER_EXPOSURE,
            ReasonCode.OUTSIDE_MEAL_WINDOW,
            ReasonCode.IN_MEAL_WINDOW,
            ReasonCode.OUTSIDE_DAYLIGHT,
            ReasonCode.IN_DAYLIGHT);

    private final DayPlanViewModel viewModel;

    public AutoSchedulePresenter(DayPlanViewModel viewModel) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Day plan view model is required");
        }
        this.viewModel = viewModel;
    }

    @Override
    public void presentPreview(AutoSchedulePreviewOutputData outputData) {
        DayPlanState current = viewModel.getState();
        Map<String, List<String>> reasonsByEvent = translateReasons(outputData.getReasons());

        List<PreviewRowView> rows = new ArrayList<>();
        for (ProposedEventData row : outputData.getRows()) {
            List<String> reasons = reasonsByEvent.getOrDefault(row.getEventId(),
                    new ArrayList<String>());
            rows.add(new PreviewRowView(row.getEventId(), row.getTitle(),
                    row.getKind() == ProposedEventData.Kind.TRAVEL
                            ? PreviewRowView.Kind.TRAVEL : PreviewRowView.Kind.ACTIVITY,
                    row.getStart(), row.getEnd(), row.isLocked(), row.isMoved(),
                    reasons.isEmpty() ? "" : reasons.get(0), reasons));
        }

        PreviewMetricsView metrics = new PreviewMetricsView(
                outputData.getTravelBeforeMinutes(), outputData.getTravelAfterMinutes(),
                outputData.getIdleBeforeMinutes(), outputData.getIdleAfterMinutes(),
                outputData.getMovedActivityCount(), outputData.getActivityCount(),
                outputData.getPracticalCostMinutes());

        viewModel.setState(new DayPlanState(current.getTripId(), current.getEvents(),
                previewHeadline(outputData), false, current.getHourlyWeather(),
                AutoScheduleStatus.PREVIEW, rows, metrics,
                outputData.getWarnings(), objectiveSummary(outputData),
                outputData.isKeptCurrentOrder(), outputData.isSearchCompletedWithinLimit(),
                travelQualityNote(outputData.getTravelQuality()),
                outputData.getScheduleFingerprint(), current.getLockedEventIds(),
                improvementViews(outputData.getImprovements())));
    }

    /**
     * Turns proven improvements into cards. Wording lives here rather than in the use case
     * for the same reason reason codes do: prose written inside the application layer is
     * prose the display cannot restate without changing the layer that computed it.
     *
     * <p>Each marker is a glyph, so the categories remain distinguishable without colour.</p>
     */
    private static List<ImprovementView> improvementViews(
            List<ScheduleImprovement> improvements) {
        List<ImprovementView> views = new ArrayList<>();
        for (ScheduleImprovement improvement : improvements) {
            switch (improvement.getType()) {
                case WAITING_REDUCED:
                    views.add(new ImprovementView("\u23f3",
                            improvement.getAmount() + " min of waiting removed",
                            "Less dead time between activities"));
                    break;
                case TRAVEL_REDUCED:
                    views.add(new ImprovementView("\u2192",
                            improvement.getAmount() + " min less travel",
                            "Shorter journeys than your current order"));
                    break;
                case LOCK_PRESERVED:
                    views.add(new ImprovementView("\u26bf",
                            "Pinned activity kept at its time",
                            improvement.getSubject()));
                    break;
                case MOVED_INTO_DAYLIGHT:
                    views.add(new ImprovementView("\u2600",
                            "Moved into daylight", improvement.getSubject()));
                    break;
                case MOVED_TO_BETTER_WEATHER:
                    views.add(new ImprovementView("\u2602",
                            "Moved to better weather", improvement.getSubject()));
                    break;
                case MEAL_MOVED_TOWARD_WINDOW:
                    views.add(new ImprovementView("\u25f4",
                            "Meal moved to a better time", improvement.getSubject()));
                    break;
                case ORDER_PRESERVED:
                    views.add(new ImprovementView("\u2261",
                            "Your original order was kept", "Nothing was reordered"));
                    break;
                default:
                    break;
            }
        }
        return views;
    }

    @Override
    public void presentApplied(AutoScheduleAppliedOutputData outputData) {
        DayPlanState current = viewModel.getState();
        List<ScheduledEvent> saved = rebuildEvents(current.getEvents(), outputData);

        viewModel.setState(new DayPlanState(outputData.getTripId(), saved,
                "Autoschedule applied. Your Day Plan has been updated.", false,
                current.getHourlyWeather(),
                AutoScheduleStatus.APPLIED, java.util.Collections.<PreviewRowView>emptyList(),
                null, java.util.Collections.<String>emptyList(), "", current.isKeptCurrentOrder(),
                true, "", "", current.getLockedEventIds()));
    }

    @Override
    public void presentConflict(AutoScheduleConflictOutputData outputData) {
        DayPlanState current = viewModel.getState();
        viewModel.setState(new DayPlanState(current.getTripId(), current.getEvents(),
                describe(outputData), true, current.getHourlyWeather(),
                AutoScheduleStatus.CONFLICT,
                java.util.Collections.<PreviewRowView>emptyList(), null,
                java.util.Collections.<String>emptyList(), "", current.isKeptCurrentOrder(),
                true, "", "", current.getLockedEventIds()));
    }

    @Override
    public void presentFailure(String message) {
        DayPlanState current = viewModel.getState();
        viewModel.setState(new DayPlanState(current.getTripId(), current.getEvents(),
                message == null || message.trim().isEmpty()
                        ? "Autoschedule could not run." : message,
                true, current.getHourlyWeather(), AutoScheduleStatus.FAILURE,
                java.util.Collections.<PreviewRowView>emptyList(), null,
                java.util.Collections.<String>emptyList(), "", current.isKeptCurrentOrder(),
                true, "", "", current.getLockedEventIds()));
    }

    /**
     * Rebuilds the saved itinerary for display.
     *
     * <p>The activities are the same ones already on screen, only re-timed, so their
     * details are taken from the events the view already holds. That keeps real activity
     * information in the Calendar without the use case having to hand an entity back
     * across the boundary.</p>
     */
    private List<ScheduledEvent> rebuildEvents(List<ScheduledEvent> existing,
                                               AutoScheduleAppliedOutputData outputData) {
        Map<String, Activity> activitiesById = new HashMap<>();
        for (ScheduledEvent event : existing) {
            if (event.getActivity() != null) {
                activitiesById.put(event.getId(), event.getActivity());
            }
        }

        List<ScheduledEvent> rebuilt = new ArrayList<>();
        for (ProposedEventData row : outputData.getSavedEvents()) {
            boolean travel = row.getKind() == ProposedEventData.Kind.TRAVEL;
            rebuilt.add(new ScheduledEvent(row.getEventId(),
                    travel ? null : activitiesById.get(row.getEventId()),
                    row.getStart(), row.getEnd(),
                    travel ? EventType.TRAVEL : EventType.ACTIVITY,
                    row.getTitle()));
        }
        return rebuilt;
    }

    private Map<String, List<String>> translateReasons(List<Reason> reasons) {
        Map<String, List<Reason>> byEvent = new LinkedHashMap<>();
        for (Reason reason : reasons) {
            byEvent.computeIfAbsent(reason.getEventId(), key -> new ArrayList<>()).add(reason);
        }

        Map<String, List<String>> sentences = new LinkedHashMap<>();
        for (Map.Entry<String, List<Reason>> entry : byEvent.entrySet()) {
            List<Reason> ordered = new ArrayList<>(entry.getValue());
            ordered.sort((left, right) -> Integer.compare(
                    REASON_PRIORITY.indexOf(left.getCode()),
                    REASON_PRIORITY.indexOf(right.getCode())));
            List<String> worded = new ArrayList<>();
            for (Reason reason : ordered) {
                worded.add(describe(reason));
            }
            sentences.put(entry.getKey(), worded);
        }
        return sentences;
    }

    /** Turns one reason code into a short phrase. This is the only place they get words. */
    String describe(Reason reason) {
        String detail = reason.getDetail();
        switch (reason.getCode()) {
            case LOCKED_BY_USER:
                return "you locked this time";
            case AVOIDS_UNAVAILABLE_PERIOD:
                return "moved clear of your unavailable time";
            case CLOSING_SOON:
                return detail.isEmpty() ? "closes soon after" : "closes at " + detail;
            case OPENS_LATER:
                return detail.isEmpty() ? "opens later" : "opens at " + detail;
            case WEATHER_EXPOSURE:
                return "poorer weather expected outdoors";
            case IN_MEAL_WINDOW:
                return "a usual mealtime";
            case OUTSIDE_MEAL_WINDOW:
                return "outside usual mealtimes, but nothing fitted better";
            case IN_DAYLIGHT:
                return "in daylight";
            case OUTSIDE_DAYLIGHT:
                return "after dark, but nothing fitted better";
            default:
                return "";
        }
    }

    /** Turns a conflict into a sentence that names what actually blocked the day. */
    String describe(AutoScheduleConflictOutputData conflict) {
        String subject = conflict.getSubject().isEmpty() ? "An activity" : conflict.getSubject();
        String unchanged = " Your Day Plan was not changed.";
        if (conflict.getKind() == ScheduleConflict.Kind.ACTIVITY_CANNOT_FIT) {
            return subject + " needs " + conflict.getRequiredMinutes()
                    + " minutes, but only " + conflict.getAvailableMinutes()
                    + " fit between its opening hours and the time you are available."
                    + unchanged;
        }
        if (conflict.getKind() == ScheduleConflict.Kind.LOCK_OUTSIDE_AVAILABILITY) {
            return subject + " is locked to a time outside the hours you said you are "
                    + "available." + unchanged;
        }
        if (conflict.getKind() == ScheduleConflict.Kind.LOCK_OUTSIDE_OPENING_HOURS) {
            return subject + " is locked to a time when it is closed." + unchanged;
        }
        if (conflict.getKind() == ScheduleConflict.Kind.LOCKS_OVERLAP) {
            return "Two things you locked overlap each other (" + subject + ")." + unchanged;
        }
        if (conflict.getKind() == ScheduleConflict.Kind.LOCK_INSIDE_UNAVAILABLE_PERIOD) {
            return subject + " is locked to a time you marked as unavailable." + unchanged;
        }
        if (conflict.getKind() == ScheduleConflict.Kind.LOCK_NOT_IN_PLAN) {
            return "A locked activity is no longer in your Day Plan. Run Autoschedule again."
                    + unchanged;
        }
        if (conflict.getKind() == ScheduleConflict.Kind.REFINED_TRAVEL_INFEASIBLE) {
            return "Once real travel times for these departure times were checked, no "
                    + "arrangement of this day worked." + unchanged;
        }
        return "No arrangement of this day fits your available hours once travel between "
                + "these activities is included." + unchanged;
    }

    private String previewHeadline(AutoSchedulePreviewOutputData data) {
        String headline = data.getMovedActivityCount() == 0
                ? "Your Day Plan is already well arranged."
                : "Proposed schedule: " + data.getMovedActivityCount() + " of "
                        + data.getActivityCount() + " activities moved.";
        if (!data.isSearchCompletedWithinLimit()) {
            headline += " Best arrangement found within the search limit.";
        }
        return headline + " Nothing changes until you choose Apply.";
    }

    /** One sentence naming what the schedule was arranged for. */
    String objectiveSummary(AutoSchedulePreviewOutputData data) {
        StringBuilder summary = new StringBuilder(
                "Arranged for less travel, fewer wasted gaps, sensible mealtimes");
        if (data.getActivePolicies().contains(PolicyId.DAYLIGHT)) {
            summary.append(" and daylight for outdoor activities");
        }
        summary.append('.');
        if (data.isKeptCurrentOrder()) {
            summary.append(" Your original order was kept where possible.");
        }
        return summary.toString();
    }

    private String travelQualityNote(TravelEstimateQuality quality) {
        if (quality == TravelEstimateQuality.ESTIMATED) {
            return "Some travel times are approximate because live routing was unavailable.";
        }
        if (quality == TravelEstimateQuality.UNKNOWN) {
            return "Travel times come from the routing service and may include estimates.";
        }
        return "";
    }
}
