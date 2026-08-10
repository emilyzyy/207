package interface_adapter.presenters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.ConstraintChipView;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.ImprovementView;
import interface_adapter.viewmodels.PreviewMetricsView;
import interface_adapter.viewmodels.PreviewRowView;
import interface_adapter.viewmodels.TimeDisplay;
import use_case.autoschedule.AutoScheduleAppliedOutputData;
import use_case.autoschedule.AutoScheduleConflictOutputData;
import use_case.autoschedule.AutoScheduleOutputBoundary;
import use_case.autoschedule.AutoSchedulePreviewOutputData;
import use_case.autoschedule.PolicyId;
import use_case.autoschedule.ProposedEventData;
import use_case.autoschedule.Reason;
import use_case.autoschedule.ReasonCode;
import use_case.autoschedule.ScheduleConflict;
import use_case.autoschedule.ScheduleImprovement;
import use_case.autoschedule.ScheduleImprovementType;
import use_case.autoschedule.TravelEstimateQuality;

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
        final DayPlanState current = viewModel.getState();
        final Map<String, List<String>> reasonsByEvent = translateReasons(outputData.getReasons());

        final List<PreviewRowView> rows = new ArrayList<>();
        for (ProposedEventData row : outputData.getRows()) {
            final List<String> reasons = reasonsByEvent.getOrDefault(row.getEventId(),
                    new ArrayList<String>());
            rows.add(new PreviewRowView(row.getEventId(), row.getTitle(),
                    row.getKind() == ProposedEventData.Kind.TRAVEL
                            ? PreviewRowView.Kind.TRAVEL : PreviewRowView.Kind.ACTIVITY,
                    row.getStart(), row.getEnd(), row.isLocked(), row.isMoved(),
                    reasons.isEmpty() ? "" : reasons.get(0), reasons));
        }

        final PreviewMetricsView metrics = new PreviewMetricsView(
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
                improvementViews(outputData.getImprovements()),
                current.getTripDates(), current.getActiveDayIndex())
                .withReasoning(constraintChips(outputData), tradeOff(outputData, metrics)));
    }

    /**
     * The requirements this schedule actively worked around.
     *
     * <p>Built from the reason codes the engine and policies already emit, so a chip is
     * evidence rather than a restatement of a settings screen. Only codes that <em>changed
     * where something went</em> qualify: a venue that happened to be open all day did not
     * constrain anything, and a chip saying so would teach the traveller to stop reading the
     * row.</p>
      * @return the result of the operation
     */
    private static List<ConstraintChipView> constraintChips(
            AutoSchedulePreviewOutputData outputData) {
        final java.util.LinkedHashMap<String, ConstraintChipView> chips = new java.util.LinkedHashMap<>();
        for (Reason reason : outputData.getReasons()) {
            final String subject = subjectFor(reason, outputData);
            switch (reason.getCode()) {
                case LOCKED_BY_USER:
                    chips.put("lock:" + subject,
                            new ConstraintChipView("\u26bf", subject + " kept at your time"));
                    break;
                case AVOIDS_UNAVAILABLE_PERIOD:
                    chips.put("unavailable",
                            new ConstraintChipView("\u25f7", "Your unavailable time kept free"));
                    break;
                case OPENS_LATER:
                    chips.put("opens:" + subject,
                            new ConstraintChipView("\u25f4", subject + " waited for opening"));
                    break;
                case CLOSING_SOON:
                    chips.put("closes:" + subject,
                            new ConstraintChipView("\u25f4", subject + " finished before closing"));
                    break;
                default:
                    break;
            }
        }
        // Worth saying only when there was something to lose: a day of one activity has not
        // "retained" anything.
        if (outputData.getActivityCount() > 1
                && outputData.getRows().size() >= outputData.getActivityCount()) {
            chips.put("retained", new ConstraintChipView("\u2713",
                    "All " + outputData.getActivityCount() + " activities kept"));
        }
        return new ArrayList<>(chips.values());
    }

    private static String subjectFor(Reason reason, AutoSchedulePreviewOutputData outputData) {
        for (ProposedEventData row : outputData.getRows()) {
            if (row.getEventId().equals(reason.getEventId())) {
                return row.getTitle();
            }
        }
        return "An activity";
    }

    /**
     * One sentence naming a disadvantage this schedule deliberately accepted.
     *
     * <p>Only when the figures themselves show the exchange: something measurable got worse
     * while something else measurable got better. An arrangement that improved on every axis
     * has no trade-off to confess, and inventing one to look thorough would be its own kind
     * of dishonesty.</p>
      * @param outputData the o ut pu td at a value
      * @return the result of the operation
     */
    private static String tradeOff(AutoSchedulePreviewOutputData outputData,
                                   PreviewMetricsView metrics) {
        final int extraTravel = -metrics.getTravelSavedMinutes();
        final int waitingRemoved = metrics.getIdleSavedMinutes();
        if (extraTravel <= 0 || waitingRemoved <= 0) {
            return "";
        }
        for (Reason reason : outputData.getReasons()) {
            if (reason.getCode() == ReasonCode.LOCKED_BY_USER) {
                return "Trade-off: " + extraTravel + " extra travel minutes to keep "
                        + subjectFor(reason, outputData) + " at the time you pinned.";
            }
        }
        return "Trade-off: " + extraTravel + " extra travel minutes to remove "
                + waitingRemoved + " minutes of waiting.";
    }

    /**
     * Turns proven improvements into cards. Wording lives here rather than in the use case
     * for the same reason reason codes do: prose written inside the application layer is
     * prose the display cannot restate without changing the layer that computed it.
     *
     * <p>Each marker is a glyph, so the categories remain distinguishable without colour.</p>
     */
    /**
     * The improvements as tiles, strongest first.
     *
     * <p>Ordered here rather than in the View because which of two true statements matters
     * more to a traveller is a presentation judgement, not a layout one. Measurable savings
     * lead, then a constraint the traveller asked for by hand, then a preference that
     * actually improved, then the explanations. Within a rank the larger figure wins, so a
     * one-minute travel saving cannot outrank an hour of waiting removed.</p>
      * @return the result of the operation
     */
    private static List<ImprovementView> improvementViews(
            List<ScheduleImprovement> improvements) {
        final List<ScheduleImprovement> ordered = new ArrayList<>(improvements);
        java.util.Collections.sort(ordered, (left, right) -> {
            final int byRank = Integer.compare(rank(left.getType()), rank(right.getType()));
            return byRank != 0 ? byRank : Integer.compare(right.getAmount(), left.getAmount());
        });

        final List<ImprovementView> views = new ArrayList<>();
        for (ScheduleImprovement improvement : ordered) {
            final ImprovementView view = tileFor(improvement);
            if (view != null) {
                views.add(view);
            }
        }
        return views;
    }

    /**
     * 0 is the strongest. See {@link #improvementViews}.
     * @param type the t yp e value
     * @return the result of the operation
     */
    private static int rank(ScheduleImprovementType type) {
        switch (type) {
            case TRAVEL_REDUCED:
            case WAITING_REDUCED:
                return 0;
            case LOCK_PRESERVED:
                return 1;
            case MOVED_INTO_DAYLIGHT:
            case MOVED_TO_BETTER_WEATHER:
            case MEAL_MOVED_TOWARD_WINDOW:
                return 2;
            default:
                return 3;
        }
    }

    private static ImprovementView tileFor(ScheduleImprovement improvement) {
        switch (improvement.getType()) {
            case WAITING_REDUCED:
                return new ImprovementView("\u25f4",
                        improvement.getAmount() + " MIN", "waiting removed");
            case TRAVEL_REDUCED:
                return new ImprovementView("\u2192",
                        improvement.getAmount() + " MIN", "less travel");
            case LOCK_PRESERVED:
                return new ImprovementView("\u26bf", "PIN KEPT", improvement.getSubject());
            case MOVED_INTO_DAYLIGHT:
                return new ImprovementView("\u2600", "DAYLIGHT", improvement.getSubject());
            case MOVED_TO_BETTER_WEATHER:
                return new ImprovementView("\u2602", "WEATHER IMPROVED",
                        improvement.getSubject());
            case MEAL_MOVED_TOWARD_WINDOW:
                return new ImprovementView("\u25d5", "BETTER MEAL TIME",
                        improvement.getSubject());
            case ORDER_PRESERVED:
                // No supporting line: "nothing was reordered" only says "order kept" again,
                // and a tile that repeats itself is one the eye learns to skip.
                return new ImprovementView("\u2261", "ORDER KEPT", "");
            default:
                return null;
        }
    }

    @Override
    public void presentApplied(AutoScheduleAppliedOutputData outputData) {
        final DayPlanState current = viewModel.getState();
        final List<ScheduledEvent> saved = rebuildEvents(current.getEvents(), outputData);

        viewModel.setState(new DayPlanState(outputData.getTripId(), saved,
                "Autoschedule applied. Your Day Plan has been updated.", false,
                current.getHourlyWeather(),
                AutoScheduleStatus.APPLIED, java.util.Collections.<PreviewRowView>emptyList(),
                null, java.util.Collections.<String>emptyList(), "", current.isKeptCurrentOrder(),
                true, "", "", current.getLockedEventIds(),
                current.getTripDates(), current.getActiveDayIndex()));
    }

    @Override
    public void presentConflict(AutoScheduleConflictOutputData outputData) {
        final DayPlanState current = viewModel.getState();
        viewModel.setState(new DayPlanState(current.getTripId(), current.getEvents(),
                describe(outputData), true, current.getHourlyWeather(),
                AutoScheduleStatus.CONFLICT,
                java.util.Collections.<PreviewRowView>emptyList(), null,
                java.util.Collections.<String>emptyList(), "", current.isKeptCurrentOrder(),
                true, "", "", current.getLockedEventIds(),
                current.getTripDates(), current.getActiveDayIndex()));
    }

    @Override
    public void presentFailure(String message) {
        final DayPlanState current = viewModel.getState();
        viewModel.setState(new DayPlanState(current.getTripId(), current.getEvents(),
                message == null || message.trim().isEmpty()
                        ? "Autoschedule could not run." : message,
                true, current.getHourlyWeather(), AutoScheduleStatus.FAILURE,
                java.util.Collections.<PreviewRowView>emptyList(), null,
                java.util.Collections.<String>emptyList(), "", current.isKeptCurrentOrder(),
                true, "", "", current.getLockedEventIds(),
                current.getTripDates(), current.getActiveDayIndex()));
    }

    /**
     * Rebuilds the saved itinerary for display.
     *
     * <p>The activities are the same ones already on screen, only re-timed, so their
     * details are taken from the events the view already holds. That keeps real activity
     * information in the Calendar without the use case having to hand an entity back
     * across the boundary.</p>
      * @param existing the e xi st in g value
      * @return the result of the operation
     */
    private List<ScheduledEvent> rebuildEvents(List<ScheduledEvent> existing,
                                               AutoScheduleAppliedOutputData outputData) {
        final Map<String, Activity> activitiesById = new HashMap<>();
        for (ScheduledEvent event : existing) {
            if (event.getActivity() != null) {
                activitiesById.put(event.getId(), event.getActivity());
            }
        }

        final List<ScheduledEvent> rebuilt = new ArrayList<>();
        for (ProposedEventData row : outputData.getSavedEvents()) {
            final boolean travel = row.getKind() == ProposedEventData.Kind.TRAVEL;
            rebuilt.add(new ScheduledEvent(row.getEventId(),
                    travel ? null : activitiesById.get(row.getEventId()),
                    row.getStart(), row.getEnd(),
                    travel ? EventType.TRAVEL : EventType.ACTIVITY,
                    row.getTitle()));
        }
        return rebuilt;
    }

    private Map<String, List<String>> translateReasons(List<Reason> reasons) {
        final Map<String, List<Reason>> byEvent = new LinkedHashMap<>();
        for (Reason reason : reasons) {
            byEvent.computeIfAbsent(reason.getEventId(), key -> new ArrayList<>()).add(reason);
        }

        final Map<String, List<String>> sentences = new LinkedHashMap<>();
        for (Map.Entry<String, List<Reason>> entry : byEvent.entrySet()) {
            final List<Reason> ordered = new ArrayList<>(entry.getValue());
            ordered.sort((left, right) -> Integer.compare(
                    REASON_PRIORITY.indexOf(left.getCode()),
                    REASON_PRIORITY.indexOf(right.getCode())));
            final List<String> worded = new ArrayList<>();
            for (Reason reason : ordered) {
                worded.add(describe(reason));
            }
            sentences.put(entry.getKey(), worded);
        }
        return sentences;
    }

    /**
     * A time or a time range from a reason detail, on the same 12-hour clock as the rest
     * of the screen.
     *
     * <p>Reasons carry machine times ("19:15", "15:00-16:00") because the use case has no
     * business knowing how a clock is written. Turning them into words is this class's job,
     * and doing it here is what stops 24-hour times leaking into a UI that is otherwise
     * entirely am/pm.</p>
      * @param detail the d et ai l value
      * @return the result of the operation
     */
    static String clock(String detail) {
        if (detail.contains("-")) {
            final int dash = detail.indexOf('-');
            return clock(detail.substring(0, dash).trim())
                    + " to " + clock(detail.substring(dash + 1).trim());
        }
        try {
            return TimeDisplay.format(java.time.LocalTime.parse(detail.trim()));
        }
        catch (java.time.format.DateTimeParseException notATime) {
            return detail;
        }
    }

    /**
     * Turns one reason code into a short phrase. This is the only place they get words.
     * @param reason the r ea so n value
     * @return the result of the operation
     */
    String describe(Reason reason) {
        final String detail = reason.getDetail();
        switch (reason.getCode()) {
            case LOCKED_BY_USER:
                return "you locked this time";
            case AVOIDS_UNAVAILABLE_PERIOD:
                return "moved clear of your unavailable time";
            case CLOSING_SOON:
                return detail.isEmpty() ? "closes soon after" : "closes at " + clock(detail);
            case OPENS_LATER:
                return detail.isEmpty() ? "opens later" : "opens at " + clock(detail);
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

    /**
     * Turns a conflict into a sentence that names what actually blocked the day.
     * @param conflict the c on fl ic t value
     * @return the result of the operation
     */
    String describe(AutoScheduleConflictOutputData conflict) {
        final String subject = conflict.getSubject().isEmpty() ? "An activity" : conflict.getSubject();
        final String unchanged = " Your Day Plan was not changed.";
        // Closed all day is its own sentence. Reported as "only 0 minutes fit" it read as a
        // window that was merely too narrow, so the traveller kept moving the activity around
        // a date it could never sit on and got the same answer every time.
        if (conflict.getKind() == ScheduleConflict.Kind.ACTIVITY_CLOSED_ON_DATE) {
            final String day = conflict.getDetail().isEmpty() ? "this day" : "on " + conflict.getDetail();
            return subject + " is closed " + day + ", so it cannot be scheduled on this date "
                    + "at any time. Remove it from the day, or choose a date it is open."
                    + unchanged;
        }
        if (conflict.getKind() == ScheduleConflict.Kind.ACTIVITY_CANNOT_FIT) {
            return subject + " needs " + conflict.getRequiredMinutes()
                    + " minutes, but its opening hours and the hours you are available "
                    + "overlap by only " + conflict.getAvailableMinutes() + "."
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

    /**
     * One sentence naming what the schedule was arranged for.
     * @param data the d at a value
     * @return the result of the operation
     */
    String objectiveSummary(AutoSchedulePreviewOutputData data) {
        final StringBuilder summary = new StringBuilder(
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
