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

        if (nothingWorthChanging(outputData)) {
            // The search agreed with the day already on screen. Presenting that as a proposal
            // would put an Apply button under a schedule identical to the saved one and invite
            // the traveller to accept a change that does not exist.
            viewModel.setState(new DayPlanState(current.getTripId(), current.getEvents(),
                    "Your Day Plan is already well arranged. Autoschedule found nothing worth "
                            + "changing.", false, current.getHourlyWeather(),
                    AutoScheduleStatus.NO_BENEFICIAL_CHANGE,
                    java.util.Collections.<PreviewRowView>emptyList(), metrics,
                    outputData.getWarnings(), "", outputData.isKeptCurrentOrder(), true,
                    travelQualityNote(outputData.getTravelQuality()), "",
                    current.getLockedEventIds(),
                    java.util.Collections.<ImprovementView>emptyList(),
                    current.getTripDates(), current.getActiveDayIndex()));
            return;
        }

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
     */
    private static List<ConstraintChipView> constraintChips(
            AutoSchedulePreviewOutputData outputData) {
        java.util.LinkedHashMap<String, ConstraintChipView> chips = new java.util.LinkedHashMap<>();
        for (Reason reason : outputData.getReasons()) {
            String subject = subjectFor(reason, outputData);
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
     */
    private static String tradeOff(AutoSchedulePreviewOutputData outputData,
                                   PreviewMetricsView metrics) {
        int extraTravel = -metrics.getTravelSavedMinutes();
        int waitingRemoved = metrics.getIdleSavedMinutes();
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
     */
    private static List<ImprovementView> improvementViews(
            List<ScheduleImprovement> improvements) {
        List<ScheduleImprovement> ordered = new ArrayList<>(improvements);
        java.util.Collections.sort(ordered, (left, right) -> {
            int byRank = Integer.compare(rank(left.getType()), rank(right.getType()));
            return byRank != 0 ? byRank : Integer.compare(right.getAmount(), left.getAmount());
        });

        List<ImprovementView> views = new ArrayList<>();
        for (ScheduleImprovement improvement : ordered) {
            ImprovementView view = tileFor(improvement);
            if (view != null) {
                views.add(view);
            }
        }
        return views;
    }

    /** 0 is the strongest. See {@link #improvementViews}. */
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
                // Not a tile. A lock's window is the activity's current time, so honouring one
                // is never a change the traveller did not already have -- every pinned day
                // would claim a benefit for standing still. It is a constraint respected, and
                // it already appears as a chip saying exactly that.
                return null;
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
        DayPlanState current = viewModel.getState();
        List<ScheduledEvent> saved = rebuildEvents(current.getEvents(), outputData);

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
        DayPlanState current = viewModel.getState();
        viewModel.setState(new DayPlanState(current.getTripId(), current.getEvents(),
                describe(outputData), true, current.getHourlyWeather(),
                AutoScheduleStatus.CONFLICT,
                java.util.Collections.<PreviewRowView>emptyList(), null,
                java.util.Collections.<String>emptyList(), "", current.isKeptCurrentOrder(),
                true, "", "", current.getLockedEventIds(),
                current.getTripDates(), current.getActiveDayIndex()));
    }

    /**
     * A hand-edited proposal, rendered by carrying its explanations forward.
     *
     * <p>Nothing is re-derived that has not changed. The remaining activities kept the times
     * the search chose, so their row reasons, their pins, their meal and daylight improvements
     * and their constraint chips are all still true; only what named the removed activity goes.
     * The two measurable tiles are recomputed from the new figures, because those genuinely
     * moved.</p>
     */
    @Override
    public void presentEditedPreview(AutoSchedulePreviewOutputData outputData,
                                     String removedEventId) {
        DayPlanState current = viewModel.getState();
        Map<String, PreviewRowView> priorRows = new HashMap<>();
        for (PreviewRowView row : current.getPreviewRows()) {
            priorRows.put(row.getEventId(), row);
        }

        List<PreviewRowView> rows = new ArrayList<>();
        for (ProposedEventData row : outputData.getRows()) {
            PreviewRowView prior = priorRows.get(row.getEventId());
            boolean isActivity = row.getKind() != ProposedEventData.Kind.TRAVEL;
            rows.add(new PreviewRowView(row.getEventId(), row.getTitle(),
                    isActivity ? PreviewRowView.Kind.ACTIVITY : PreviewRowView.Kind.TRAVEL,
                    row.getStart(), row.getEnd(), row.isLocked(), row.isMoved(),
                    prior == null ? "" : prior.getReason(),
                    prior == null ? java.util.Collections.<String>emptyList()
                            : prior.getAllReasons()));
        }

        PreviewMetricsView metrics = new PreviewMetricsView(
                outputData.getTravelBeforeMinutes(), outputData.getTravelAfterMinutes(),
                outputData.getIdleBeforeMinutes(), outputData.getIdleAfterMinutes(),
                outputData.getMovedActivityCount(), outputData.getActivityCount(),
                outputData.getPracticalCostMinutes());

        String removedName = priorRows.containsKey(removedEventId)
                ? priorRows.get(removedEventId).getTitle() : "";
        List<ImprovementView> tiles = carriedTiles(current.getImprovements(), metrics, removedName);
        List<ConstraintChipView> chips = carriedChips(current.getConstraintChips(), removedName,
                outputData.getActivityCount());

        viewModel.setState(new DayPlanState(current.getTripId(), current.getEvents(),
                "Proposed schedule: " + outputData.getMovedActivityCount() + " of "
                        + outputData.getActivityCount()
                        + " activities moved. Nothing changes until you choose Apply.",
                false, current.getHourlyWeather(), AutoScheduleStatus.PREVIEW, rows, metrics,
                current.getWarnings(), current.getObjectiveSummary(),
                current.isKeptCurrentOrder(), current.isSearchCompletedWithinLimit(),
                current.getTravelQualityNote(), current.getPreviewFingerprint(),
                current.getLockedEventIds(), tiles,
                current.getTripDates(), current.getActiveDayIndex())
                .withReasoning(chips, tradeOffFor(metrics, chips)));
    }

    /**
     * Re-derived from the edited figures, never carried forward.
     *
     * <p>A trade-off is a claim about an exchange this particular schedule made. Removing an
     * activity changes both sides of it, and a sentence that was true of the original proposal
     * can be plainly false of the edited one — "four extra travel minutes to keep your pinned
     * lunch" survives the lunch being deleted unless it is recomputed.</p>
     */
    private static String tradeOffFor(PreviewMetricsView metrics,
                                      List<ConstraintChipView> chips) {
        int extraTravel = -metrics.getTravelSavedMinutes();
        int waitingRemoved = metrics.getIdleSavedMinutes();
        if (extraTravel <= 0 || waitingRemoved <= 0) {
            return "";
        }
        for (ConstraintChipView chip : chips) {
            if (chip.getLabel().contains("kept at your time")) {
                return "Trade-off: " + extraTravel + " extra travel minutes to keep "
                        + chip.getLabel().replace(" kept at your time", "")
                        + " at the time you pinned.";
            }
        }
        return "Trade-off: " + extraTravel + " extra travel minutes to remove "
                + waitingRemoved + " minutes of waiting.";
    }

    /** The old tiles minus anything about the removed activity, with the figures refreshed. */
    private static List<ImprovementView> carriedTiles(List<ImprovementView> prior,
                                                      PreviewMetricsView metrics,
                                                      String removedName) {
        List<ImprovementView> tiles = new ArrayList<>();
        if (metrics.getIdleSavedMinutes() > 0) {
            tiles.add(new ImprovementView("\u25f4",
                    metrics.getIdleSavedMinutes() + " MIN", "waiting removed"));
        }
        if (metrics.getTravelSavedMinutes() > 0) {
            tiles.add(new ImprovementView("\u2192",
                    metrics.getTravelSavedMinutes() + " MIN", "less travel"));
        }
        for (ImprovementView tile : prior) {
            boolean measurable = "waiting removed".equals(tile.getSecondary())
                    || "less travel".equals(tile.getSecondary());
            boolean aboutTheRemoved = !removedName.isEmpty()
                    && tile.getSecondary().contains(removedName);
            if (!measurable && !aboutTheRemoved) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    private static List<ConstraintChipView> carriedChips(List<ConstraintChipView> prior,
                                                         String removedName, int activityCount) {
        List<ConstraintChipView> chips = new ArrayList<>();
        for (ConstraintChipView chip : prior) {
            boolean aboutTheRemoved = !removedName.isEmpty()
                    && chip.getLabel().contains(removedName);
            boolean isTheCountChip = chip.getLabel().startsWith("All ");
            if (!aboutTheRemoved && !isTheCountChip) {
                chips.add(chip);
            }
        }
        if (activityCount > 1) {
            chips.add(new ConstraintChipView("\u2713", "All " + activityCount + " kept"));
        }
        return chips;
    }

    /**
     * A refused draft edit: the proposal stays exactly where it is and says why it could not
     * change. Deliberately not a failure state — the Preview is still valid and still
     * applicable, so Apply and Cancel remain available.
     */
    @Override
    public void presentDraftEditRefused(String reason) {
        DayPlanState current = viewModel.getState();
        viewModel.setState(new DayPlanState(current.getTripId(), current.getEvents(),
                reason, true, current.getHourlyWeather(), AutoScheduleStatus.PREVIEW,
                current.getPreviewRows(), current.getMetrics(), current.getWarnings(),
                current.getObjectiveSummary(), current.isKeptCurrentOrder(),
                current.isSearchCompletedWithinLimit(), current.getTravelQualityNote(),
                current.getPreviewFingerprint(), current.getLockedEventIds(),
                current.getImprovements(), current.getTripDates(), current.getActiveDayIndex()));
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

    /**
     * A time or a time range from a reason detail, on the same 12-hour clock as the rest
     * of the screen.
     *
     * <p>Reasons carry machine times ("19:15", "15:00-16:00") because the use case has no
     * business knowing how a clock is written. Turning them into words is this class's job,
     * and doing it here is what stops 24-hour times leaking into a UI that is otherwise
     * entirely am/pm.</p>
     */
    static String clock(String detail) {
        if (detail.contains("-")) {
            int dash = detail.indexOf('-');
            return clock(detail.substring(0, dash).trim())
                    + " to " + clock(detail.substring(dash + 1).trim());
        }
        try {
            return TimeDisplay.format(java.time.LocalTime.parse(detail.trim()));
        } catch (java.time.format.DateTimeParseException notATime) {
            return detail;
        }
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

    /** Turns a conflict into a sentence that names what actually blocked the day. */
    String describe(AutoScheduleConflictOutputData conflict) {
        String subject = conflict.getSubject().isEmpty() ? "An activity" : conflict.getSubject();
        String unchanged = " Your Day Plan was not changed.";
        // Closed all day is its own sentence. Reported as "only 0 minutes fit" it read as a
        // window that was merely too narrow, so the traveller kept moving the activity around
        // a date it could never sit on and got the same answer every time.
        if (conflict.getKind() == ScheduleConflict.Kind.ACTIVITY_CLOSED_ON_DATE) {
            String day = conflict.getDetail().isEmpty() ? "this day" : "on " + conflict.getDetail();
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
            if (conflict.getLockedWindow() != null && conflict.getUnavailableWindow() != null) {
                return subject + " is locked from "
                        + TimeDisplay.format(conflict.getLockedWindow().getStart()) + " to "
                        + TimeDisplay.format(conflict.getLockedWindow().getEnd())
                        + ", which overlaps the time you marked as unavailable from "
                        + TimeDisplay.format(conflict.getUnavailableWindow().getStart()) + " to "
                        + TimeDisplay.format(conflict.getUnavailableWindow().getEnd())
                        + ". Unlock it, change the unavailable period, or move the activity."
                        + unchanged;
            }
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

    /**
     * Whether this proposal is simply the day that is already saved.
     *
     * <p>Nothing moved, and no figure improved. The engine reaching the same arrangement is the
     * right answer — it means the traveller had already arranged the day well — but it is an
     * answer, not an offer.</p>
     *
     * <p>A proposal that moves something is never suppressed, however its figures look: a day
     * rearranged to clear a newly declared unavailable period is worth showing even when it
     * costs travel, and the trade-off strip is there to say so.</p>
     */
    private static boolean nothingWorthChanging(AutoSchedulePreviewOutputData data) {
        if (data.getMovedActivityCount() > 0) {
            return false;
        }
        if (data.getTravelAfterMinutes() < data.getTravelBeforeMinutes()
                || data.getIdleAfterMinutes() < data.getIdleBeforeMinutes()) {
            return false;
        }
        for (ScheduleImprovement improvement : data.getImprovements()) {
            if (improvement.getType() != ScheduleImprovementType.ORDER_PRESERVED) {
                return false;
            }
        }
        return true;
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
     * One sentence naming what this schedule actually achieved.
     *
     * <p>It used to be assembled from which preferences were switched <em>on</em>, so it
     * announced "Arranged for less travel, fewer wasted gaps" whatever came out — including
     * over a proposal whose own figures, printed directly above it, showed more travel and
     * more waiting than the day it replaced. Every other claim on the screen has to be earned;
     * this one asserted the objective and called it the outcome.</p>
     *
     * <p>So each clause is now conditional on the evidence, and a schedule that improved
     * nothing measurable says so rather than reaching for the same words.</p>
     */
    String objectiveSummary(AutoSchedulePreviewOutputData data) {
        List<String> achieved = new ArrayList<>();
        if (data.getTravelBeforeMinutes() > data.getTravelAfterMinutes()) {
            achieved.add("less travel");
        }
        if (data.getIdleBeforeMinutes() > data.getIdleAfterMinutes()) {
            achieved.add("fewer wasted gaps");
        }
        for (ScheduleImprovement improvement : data.getImprovements()) {
            switch (improvement.getType()) {
                case MEAL_MOVED_TOWARD_WINDOW:
                    addOnce(achieved, "sensible mealtimes");
                    break;
                case MOVED_INTO_DAYLIGHT:
                    addOnce(achieved, "daylight for outdoor activities");
                    break;
                case MOVED_TO_BETTER_WEATHER:
                    addOnce(achieved, "better weather");
                    break;
                default:
                    break;
            }
        }

        StringBuilder summary = new StringBuilder();
        if (achieved.isEmpty()) {
            // The honest empty case. A day can be worth proposing for the constraints it
            // respects even when no figure improves, and saying that is better than
            // borrowing the words for gains it did not make.
            summary.append("This arrangement respects your constraints; it does not reduce "
                    + "travel or waiting.");
        } else {
            summary.append("Arranged for ").append(join(achieved)).append('.');
        }
        if (data.isKeptCurrentOrder()) {
            summary.append(" Your original order was kept where possible.");
        }
        return summary.toString();
    }

    private static void addOnce(List<String> into, String clause) {
        if (!into.contains(clause)) {
            into.add(clause);
        }
    }

    /** "a", "a and b", "a, b and c" — read aloud rather than comma-spliced. */
    private static String join(List<String> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                text.append(i == parts.size() - 1 ? " and " : ", ");
            }
            text.append(parts.get(i));
        }
        return text.toString();
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
