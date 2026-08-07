package closeai.application.autoschedule;

import closeai.application.autoschedule.engine.PlanValidator;
import closeai.application.autoschedule.engine.ScheduleEngine;
import closeai.application.autoschedule.engine.SchedulePlanRebuilder;
import closeai.application.autoschedule.engine.ScheduleSearchResult;
import closeai.application.autoschedule.engine.SearchBudget;
import closeai.application.autoschedule.policy.SoftPolicy;
import closeai.application.ports.TripRepository;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates one Autoschedule request.
 *
 * <p>The division of labour matters: this class gathers what the day needs — the trip,
 * travel estimates, a forecast — hands a self-contained problem to the pure engine, and
 * turns the answer into display data. It contains no search logic, and the engine
 * contains no input or output. That separation is what lets the whole feature be tested
 * without a network, and it is why each class has one reason to change.</p>
 *
 * <p>Travel is fetched twice by design. Before searching, estimates are prefetched for a
 * handful of departure periods so the search can compare orders that travel at different
 * times of day. Afterwards the chosen order is re-estimated for the times it will really
 * be travelled, re-timed, and re-validated. If those truer numbers break the schedule the
 * search runs again with them; only when that fails does the user get a conflict. A
 * schedule known to be invalid is never shown.</p>
 */
public final class AutoScheduleInteractor implements AutoScheduleInputBoundary {

    /** How many times the search may be repeated with corrected travel times. */
    static final int MAX_REFINEMENT_ROUNDS = 2;

    private final TripRepository trips;
    private final TravelTimeEstimator travelEstimator;
    private final WeatherContextGateway weatherGateway;
    private final AutoScheduleOutputBoundary presenter;
    private final List<SoftPolicy> registeredPolicies;
    private final ScheduleEngine engine;
    private final TravelMatrixPrefetcher prefetcher;
    private final SchedulePlanRebuilder rebuilder;
    private final PlanValidator planValidator;
    private final ProblemValidator problemValidator;
    private final ReasonCollector reasonCollector;
    private final ScheduleImprovementFinder improvementFinder;

    public AutoScheduleInteractor(TripRepository trips, TravelTimeEstimator travelEstimator,
                                  WeatherContextGateway weatherGateway,
                                  AutoScheduleOutputBoundary presenter,
                                  List<SoftPolicy> registeredPolicies,
                                  ScheduleEngine engine) {
        if (trips == null || travelEstimator == null || weatherGateway == null
                || presenter == null || engine == null) {
            throw new IllegalArgumentException("Autoschedule dependencies are required");
        }
        this.trips = trips;
        this.travelEstimator = travelEstimator;
        this.weatherGateway = weatherGateway;
        this.presenter = presenter;
        this.registeredPolicies = Collections.unmodifiableList(new ArrayList<>(
                registeredPolicies == null ? Collections.<SoftPolicy>emptyList()
                        : registeredPolicies));
        this.engine = engine;
        this.prefetcher = new TravelMatrixPrefetcher(travelEstimator);
        this.rebuilder = new SchedulePlanRebuilder(engine.placer());
        this.planValidator = new PlanValidator();
        this.problemValidator = new ProblemValidator();
        this.reasonCollector = new ReasonCollector();
        this.improvementFinder = new ScheduleImprovementFinder();
    }

    @Override
    public void preview(AutoScheduleInputData inputData) {
        if (inputData == null) {
            presenter.presentFailure("Autoschedule settings are required");
            return;
        }
        Trip trip = findTrip(inputData.getTripId());
        if (trip == null) {
            return;
        }

        List<ScheduledEvent> activityEvents = activityEventsOf(trip);
        if (activityEvents.isEmpty()) {
            presenter.presentFailure("Add activities to the Day Plan before running Autoschedule");
            return;
        }
        if (activityEvents.size() > MaximumActivities.SUPPORTED) {
            presenter.presentFailure("Autoschedule supports up to " + MaximumActivities.SUPPORTED
                    + " activities in one day; this plan has " + activityEvents.size());
            return;
        }

        TimeWindow availability = validatedAvailability(trip, inputData);
        if (availability == null) {
            return;
        }
        List<ScheduleTask> tasks = buildTasks(activityEvents, inputData, trip.getDate());
        if (tasks == null) {
            return;
        }

        ScheduleConflict invalid = problemValidator.validate(availability, tasks,
                inputData.getUnavailableWindows());
        if (invalid != null) {
            presenter.presentConflict(new AutoScheduleConflictOutputData(invalid));
            return;
        }

        TransportationMode mode = inputData.getTransportationMode() == null
                ? trip.getTransportationMode() : inputData.getTransportationMode();

        TravelMatrix matrix;
        try {
            matrix = prefetcher.prefetch(tasks, mode, trip.getDate(), availability);
        } catch (RuntimeException exception) {
            presenter.presentFailure(
                    "Travel times are unavailable right now, so no schedule was produced. "
                            + "Your Day Plan was not changed.");
            return;
        }

        List<String> warnings = new ArrayList<>();
        addOpeningHoursWarnings(tasks, warnings);
        WeatherContext weather = weatherFor(trip, inputData.isConsiderWeather(), warnings);

        SchedulingPreferences preferences = SchedulingPreferences.builtIn(registeredPolicies,
                inputData.isKeepCurrentOrder(), new PolicyContext(weather));

        RefinementOutcome outcome = searchWithExactTravel(availability, tasks,
                inputData.getUnavailableWindows(), matrix, preferences, mode, trip.getDate());

        if (outcome.plan == null) {
            presenter.presentConflict(new AutoScheduleConflictOutputData(outcome.conflict));
            return;
        }
        presenter.presentPreview(buildPreview(trip, activityEvents, outcome, preferences,
                inputData.getUnavailableWindows(), warnings, mode));
    }

    /**
     * The forecast this run will actually schedule against.
     *
     * <p>Two gates, in order. The traveller's tick comes first: weather not asked for is
     * weather not fetched, so an unticked box costs nothing and contributes nothing. Then
     * the forecast itself has to be good enough — this class, not the dialog, decides that.
     * The dialog only enables its checkbox when the forecast can distinguish times, but a
     * dialog can be stale or simply wrong, and a preference that quietly did nothing while
     * being listed as an objective would be a lie about how the day was arranged. So a
     * coarse or missing forecast contributes zero here and says so in a warning, and the
     * schedule is produced either way.</p>
     */
    private WeatherContext weatherFor(Trip trip, boolean requested, List<String> warnings) {
        if (!requested) {
            return WeatherContext.unavailable();
        }
        WeatherContext weather;
        try {
            weather = weatherGateway.contextFor(trip);
        } catch (RuntimeException exception) {
            // The gateway contract says failures come back as an unavailable context, but a
            // schedule must not be lost if an implementation throws instead.
            weather = WeatherContext.unavailable();
        }
        if (!weather.isAvailable()) {
            warnings.add("You asked for weather to be considered, but no forecast was "
                    + "available, so the schedule was arranged using time and travel "
                    + "information only.");
        } else if (!weather.canDistinguishTimes()) {
            warnings.add("The forecast covers the whole day rather than each hour, so weather "
                    + "could not influence the timing of outdoor activities.");
        }
        return weather;
    }

    @Override
    public WeatherOption weatherOptionFor(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return WeatherOption.unavailable(WeatherOption.NO_FORECAST);
        }
        Optional<Trip> found = trips.findById(tripId.trim());
        if (!found.isPresent()) {
            return WeatherOption.unavailable(WeatherOption.NO_FORECAST);
        }
        // Deliberately silent: this answers a question the dialog asks while drawing
        // itself. Reporting a missing forecast as a failure would put an error on screen
        // for something the user never did.
        return weatherGateway.optionFor(found.get());
    }

    /**
     * Searches, then re-estimates the winning order at its real departure times.
     *
     * <p>When the corrected numbers still fit, that re-timed schedule is the answer.
     * When they do not, they are written into the travel cache and the search runs again
     * knowing them — bounded, so a pathological day terminates with a conflict rather
     * than looping.</p>
     */
    private RefinementOutcome searchWithExactTravel(TimeWindow availability,
                                                    List<ScheduleTask> tasks,
                                                    List<TimeWindow> unavailableWindows,
                                                    TravelMatrix matrix,
                                                    SchedulingPreferences preferences,
                                                    TransportationMode mode,
                                                    java.time.LocalDate date) {
        TravelMatrix currentMatrix = matrix;
        ScheduleConflict lastConflict = ScheduleConflict.noFeasibleOrder();
        boolean withinLimit = true;

        for (int round = 0; round <= MAX_REFINEMENT_ROUNDS; round++) {
            ScheduleProblem problem = new ScheduleProblem(availability, tasks,
                    unavailableWindows, currentMatrix, preferences);
            ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());
            withinLimit = result.isCompletedWithinLimit();
            if (!result.isFound()) {
                return RefinementOutcome.conflict(result.getConflict());
            }

            Map<TravelLegKey, TravelEstimate> exact = exactEstimatesFor(result.getPlan(), mode, date);
            if (exact.isEmpty()) {
                return RefinementOutcome.plan(result.getPlan(), problem, withinLimit);
            }

            TravelMatrix refined = currentMatrix.withOverrides(exact);
            ScheduleProblem refinedProblem = new ScheduleProblem(availability, tasks,
                    unavailableWindows, refined, preferences);
            SchedulePlan rebuilt = rebuilder.rebuild(refinedProblem,
                    result.getPlan().orderedEventIds());

            if (rebuilt != null && planValidator.validate(refinedProblem, rebuilt) == null) {
                return RefinementOutcome.plan(rebuilt, refinedProblem, withinLimit);
            }
            lastConflict = ScheduleConflict.refinedTravelInfeasible();
            currentMatrix = refined;
        }
        return RefinementOutcome.conflict(lastConflict);
    }

    /**
     * Re-estimates each leg for the moment it will actually be travelled, keeping only
     * the values that differ from the bucketed estimate the search used.
     */
    private Map<TravelLegKey, TravelEstimate> exactEstimatesFor(SchedulePlan plan,
                                                                TransportationMode mode,
                                                                java.time.LocalDate date) {
        Map<TravelLegKey, TravelEstimate> changed = new HashMap<>();
        List<PlacedActivity> placements = plan.getPlacements();
        for (int i = 1; i < placements.size(); i++) {
            PlacedActivity current = placements.get(i);
            if (!current.hasTravel()) {
                continue;
            }
            ScheduleTask from = placements.get(i - 1).getTask();
            ScheduleTask to = current.getTask();
            LocalTime departure = current.getTravelDeparture();
            TravelEstimate exact = travelEstimator.estimate(
                    from.getActivity().getLocation(), to.getActivity().getLocation(), mode,
                    LocalDateTime.of(date, departure));
            if (exact.getMinutes() != current.getTravelMinutesBefore()) {
                changed.put(new TravelLegKey(from.getEventId(), to.getEventId(), departure), exact);
            }
        }
        return changed;
    }

    @Override
    public void apply(AutoScheduleApplyInputData inputData) {
        if (inputData == null) {
            presenter.presentFailure("Nothing to apply");
            return;
        }
        Trip trip = findTrip(inputData.getTripId());
        if (trip == null) {
            return;
        }
        if (inputData.getProposedEvents().isEmpty()) {
            presenter.presentFailure("Nothing to apply");
            return;
        }

        ScheduleFingerprint current = ScheduleFingerprint.of(trip.getScheduledEvents());
        if (!current.getValue().equals(inputData.getExpectedFingerprint())) {
            presenter.presentFailure("The Day Plan changed after this Preview was generated. "
                    + "Run Autoschedule again.");
            return;
        }

        Map<String, Activity> activitiesByEventId = new HashMap<>();
        for (ScheduledEvent event : activityEventsOf(trip)) {
            activitiesByEventId.put(event.getId(), event.getActivity());
        }

        List<ScheduledEvent> events = new ArrayList<>();
        for (ProposedEventData row : inputData.getProposedEvents()) {
            if (row.getKind() == ProposedEventData.Kind.TRAVEL) {
                events.add(new ScheduledEvent(row.getEventId(), null, row.getStart(), row.getEnd(),
                        EventType.TRAVEL, row.getTitle()));
                continue;
            }
            Activity activity = activitiesByEventId.get(row.getEventId());
            if (activity == null) {
                presenter.presentFailure("This Preview refers to an activity that is no longer "
                        + "in the Day Plan. Run Autoschedule again.");
                return;
            }
            events.add(new ScheduledEvent(row.getEventId(), activity, row.getStart(), row.getEnd(),
                    EventType.ACTIVITY, ""));
        }

        Trip saved;
        try {
            saved = trips.save(trip.copyWithSchedule(events));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            presenter.presentFailure("The proposed schedule could not be saved: "
                    + exception.getMessage());
            return;
        }

        presenter.presentApplied(new AutoScheduleAppliedOutputData(saved.getId(),
                inputData.getProposedEvents(),
                ScheduleFingerprint.of(saved.getScheduledEvents()).getValue()));
    }

    private Trip findTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            presenter.presentFailure("Trip id is required");
            return null;
        }
        Optional<Trip> found = trips.findById(tripId.trim());
        if (!found.isPresent()) {
            presenter.presentFailure("Trip not found");
            return null;
        }
        return found.get();
    }

    private static List<ScheduledEvent> activityEventsOf(Trip trip) {
        List<ScheduledEvent> events = new ArrayList<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getEventType() == EventType.ACTIVITY && event.getActivity() != null) {
                events.add(event);
            }
        }
        return events;
    }

    /**
     * The availability window for this run, which may narrow the trip's hours but never
     * widen them: the Trip entity refuses to hold events outside its own window, so a
     * wider request would produce a schedule that could not be saved.
     */
    private TimeWindow validatedAvailability(Trip trip, AutoScheduleInputData inputData) {
        LocalTime start = inputData.getAvailableStart() == null
                ? trip.getStartTime() : inputData.getAvailableStart();
        LocalTime end = inputData.getAvailableEnd() == null
                ? trip.getEndTime() : inputData.getAvailableEnd();

        if (!end.isAfter(start)) {
            presenter.presentFailure("Available until must be later than available from");
            return null;
        }
        if (start.isBefore(trip.getStartTime()) || end.isAfter(trip.getEndTime())) {
            presenter.presentFailure("Available hours must be within the trip's hours ("
                    + trip.getStartTime() + " to " + trip.getEndTime()
                    + "). To extend your day, edit the trip settings.");
            return null;
        }
        return new TimeWindow(start, end);
    }

    /**
     * Warns only about venues that are on record as shut for the whole trip date.
     *
     * <p>Nothing is said about venues with no published hours, which is most of them. The
     * scheduler treats those as unconstrained beyond their general daily window, and that is
     * the ordinary case rather than a problem — warning about it put a caution on almost
     * every schedule and made the real warnings worth less.</p>
     */
    private static void addOpeningHoursWarnings(List<ScheduleTask> tasks,
                                                List<String> warnings) {
        List<String> closed = new ArrayList<>();
        for (ScheduleTask task : tasks) {
            if (task.isClosedAllDay()) {
                closed.add(task.getActivity().getName());
            }
        }
        if (!closed.isEmpty()) {
            warnings.add(namesOf(closed) + (closed.size() == 1 ? " is" : " are")
                    + " closed on this date, so " + (closed.size() == 1 ? "it" : "they")
                    + " could not be scheduled.");
        }
    }

    /** "A", "A and B", "A, B and C", then "A, B and 3 more" so a long day stays readable. */
    private static String namesOf(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() == 2) {
            return names.get(0) + " and " + names.get(1);
        }
        if (names.size() == 3) {
            return names.get(0) + ", " + names.get(1) + " and " + names.get(2);
        }
        return names.get(0) + ", " + names.get(1) + " and " + (names.size() - 2) + " more";
    }

    /**
     * Turns the Day Plan's events into scheduling tasks, applying the user's locks.
     *
     * <p>The trip date is what turns a venue's week of opening hours into the windows for
     * <em>this</em> day. Resolving the weekday here rather than in the engine keeps the
     * search working on one day's plain time windows, and means nothing below this point
     * needs a calendar.</p>
     */
    private List<ScheduleTask> buildTasks(List<ScheduledEvent> activityEvents,
                                          AutoScheduleInputData inputData,
                                          LocalDate tripDate) {
        List<String> knownIds = new ArrayList<>();
        for (ScheduledEvent event : activityEvents) {
            knownIds.add(event.getId());
        }
        for (String lockedId : inputData.getLockedEventIds()) {
            if (!knownIds.contains(lockedId)) {
                presenter.presentConflict(new AutoScheduleConflictOutputData(
                        ScheduleConflict.of(ScheduleConflict.Kind.LOCK_NOT_IN_PLAN, lockedId, "")));
                return null;
            }
        }

        List<ScheduleTask> tasks = new ArrayList<>();
        for (int index = 0; index < activityEvents.size(); index++) {
            ScheduledEvent event = activityEvents.get(index);
            int duration = (event.getEndTime().toSecondOfDay()
                    - event.getStartTime().toSecondOfDay()) / 60;
            if (duration <= 0) {
                presenter.presentFailure("An activity in the Day Plan has no duration");
                return null;
            }
            TimeWindow lockedAt = inputData.getLockedEventIds().contains(event.getId())
                    ? new TimeWindow(event.getStartTime(), event.getEndTime()) : null;
            tasks.add(new ScheduleTask(event.getId(), event.getActivity(), duration, index,
                    lockedAt, tripDate));
        }
        return tasks;
    }

    private AutoSchedulePreviewOutputData buildPreview(Trip trip,
                                                       List<ScheduledEvent> originalEvents,
                                                       RefinementOutcome outcome,
                                                       SchedulingPreferences preferences,
                                                       List<TimeWindow> unavailableWindows,
                                                       List<String> warnings,
                                                       TransportationMode mode) {
        SchedulePlan plan = outcome.plan;
        Map<String, ScheduledEvent> originalById = new HashMap<>();
        for (ScheduledEvent event : originalEvents) {
            originalById.put(event.getId(), event);
        }

        List<ProposedEventData> rows = new ArrayList<>();
        int movedCount = 0;
        for (PlacedActivity placed : plan.getPlacements()) {
            TimeWindow travel = placed.travelWindow();
            if (travel != null) {
                rows.add(new ProposedEventData("travel-" + placed.getTask().getEventId(), "",
                        "Travel to " + placed.getTask().getActivity().getName(),
                        ProposedEventData.Kind.TRAVEL, travel.getStart(), travel.getEnd(),
                        false, false));
            }
            ScheduledEvent original = originalById.get(placed.getTask().getEventId());
            boolean moved = original != null && !original.getStartTime().equals(placed.getStart());
            if (moved) {
                movedCount++;
            }
            rows.add(new ProposedEventData(placed.getTask().getEventId(),
                    placed.getTask().getActivity().getId(),
                    placed.getTask().getActivity().getName(),
                    ProposedEventData.Kind.ACTIVITY, placed.getStart(), placed.getEnd(),
                    placed.getTask().isLocked(), moved));
        }

        List<Reason> reasons = reasonCollector.collect(plan, preferences, unavailableWindows);
        // Measured with the journeys the current order implies, not only the travel rows it
        // happens to contain; see ScheduleMetrics for why the simpler reading flattered us.
        ScheduleMetrics before = ScheduleMetrics.ofExistingSchedule(trip.getScheduledEvents(),
                travelEstimator, mode, trip.getDate());

        List<ScheduleImprovement> improvements = improvementFinder.find(originalEvents, plan,
                preferences, before, plan.totalTravelMinutes(),
                plan.totalAvoidableIdleMinutes());

        return new AutoSchedulePreviewOutputData(rows, before.getTravelMinutes(),
                plan.totalTravelMinutes(), before.getIdleMinutes(),
                plan.totalAvoidableIdleMinutes(), movedCount, originalEvents.size(),
                reasons, warnings, preferences.activeIds(),
                ScheduleFingerprint.of(trip.getScheduledEvents()).getValue(),
                outcome.searchCompletedWithinLimit,
                outcome.problem.getTravel().weakestQuality(),
                preferences.isKeepCurrentOrder(),
                improvements,
                plan.getScore().practicalCostMinutes());
    }

    /** Result of the search-and-refine loop: a validated plan, or the reason there is none. */
    private static final class RefinementOutcome {
        private final SchedulePlan plan;
        private final ScheduleProblem problem;
        private final ScheduleConflict conflict;
        private final boolean searchCompletedWithinLimit;

        private RefinementOutcome(SchedulePlan plan, ScheduleProblem problem,
                                  ScheduleConflict conflict, boolean searchCompletedWithinLimit) {
            this.plan = plan;
            this.problem = problem;
            this.conflict = conflict;
            this.searchCompletedWithinLimit = searchCompletedWithinLimit;
        }

        static RefinementOutcome plan(SchedulePlan plan, ScheduleProblem problem,
                                      boolean withinLimit) {
            return new RefinementOutcome(plan, problem, null, withinLimit);
        }

        static RefinementOutcome conflict(ScheduleConflict conflict) {
            return new RefinementOutcome(null, null, conflict, true);
        }
    }

    /** The size beyond which a single day is a data-entry problem, not a scheduling one. */
    static final class MaximumActivities {
        static final int SUPPORTED = 15;

        private MaximumActivities() {
        }
    }
}
