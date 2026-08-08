package trippy.adapters.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import trippy.adapters.viewmodels.AutoScheduleStatus;
import trippy.adapters.viewmodels.DayPlanState;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.adapters.viewmodels.PreviewRowView;
import trippy.application.autoschedule.AutoScheduleAppliedOutputData;
import trippy.application.autoschedule.AutoScheduleConflictOutputData;
import trippy.application.autoschedule.AutoSchedulePreviewOutputData;
import trippy.application.autoschedule.PolicyId;
import trippy.application.autoschedule.ProposedEventData;
import trippy.application.autoschedule.Reason;
import trippy.application.autoschedule.ReasonCode;
import trippy.application.autoschedule.ScheduleConflict;
import trippy.application.autoschedule.ScheduleImprovement;
import trippy.application.autoschedule.TravelEstimateQuality;
import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.EventType;
import trippy.domain.valueobjects.IndoorOutdoorType;
import trippy.domain.valueobjects.Location;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutoSchedulePresenterTest {

    private static Activity activity(String id) {
        return new Activity(id, "Name of " + id, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(9, 0), LocalTime.of(21, 0), IndoorOutdoorType.INDOOR, "none");
    }

    private static ScheduledEvent existingEvent(String id, int hour) {
        return new ScheduledEvent(id, activity(id), LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0), EventType.ACTIVITY, "");
    }

    private static DayPlanViewModel viewModelWith(ScheduledEvent... events) {
        return new DayPlanViewModel(new DayPlanState("trip-1", Arrays.asList(events), "", false));
    }

    private static ProposedEventData activityRow(String id, int hour, boolean moved) {
        return new ProposedEventData(id, id, "Name of " + id, ProposedEventData.Kind.ACTIVITY,
                LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), false, moved);
    }

    private static ProposedEventData travelRow(String id, int hour, int minutes) {
        return new ProposedEventData("travel-" + id, "", "Travel to " + id,
                ProposedEventData.Kind.TRAVEL, LocalTime.of(hour, 0),
                LocalTime.of(hour, 0).plusMinutes(minutes), false, false);
    }

    private static AutoSchedulePreviewOutputData preview(List<ProposedEventData> rows,
                                                         List<Reason> reasons,
                                                         List<String> warnings,
                                                         boolean keptOrder,
                                                         boolean withinLimit,
                                                         TravelEstimateQuality quality) {
        return new AutoSchedulePreviewOutputData(rows, 80, 40, 60, 20, 2, 3, reasons, warnings,
                Arrays.asList(PolicyId.WEATHER, PolicyId.MEAL_TIME, PolicyId.DAYLIGHT,
                        PolicyId.REDUCE_IDLE),
                "fingerprint-1", withinLimit, quality, keptOrder,
                Collections.<ScheduleImprovement>emptyList(), 60);
    }

    @Test
    void aPreviewNeverTouchesTheRealItinerary() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9), existingEvent("b", 14));
        List<ScheduledEvent> before = viewModel.getState().getEvents();

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, true), activityRow("b", 12, true)),
                Collections.emptyList(), Collections.emptyList(), true, true,
                TravelEstimateQuality.ROUTED));

        DayPlanState state = viewModel.getState();
        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus());
        assertEquals(before, state.getEvents(),
                "the Calendar must keep showing the agreed itinerary during a preview");
        assertEquals(2, state.getPreviewRows().size());
    }

    @Test
    void previewCarriesMetricsAndFingerprint() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, true)), Collections.emptyList(),
                Collections.emptyList(), true, true, TravelEstimateQuality.ROUTED));

        DayPlanState state = viewModel.getState();
        assertNotNull(state.getMetrics());
        assertEquals(40, state.getMetrics().getTravelSavedMinutes());
        assertEquals(40, state.getMetrics().getIdleSavedMinutes());
        assertEquals("fingerprint-1", state.getPreviewFingerprint());
    }

    @Test
    void reasonCodesBecomeSentences() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));
        List<Reason> reasons = Arrays.asList(
                new Reason("a", ReasonCode.CLOSING_SOON, "17:00"),
                new Reason("a", ReasonCode.IN_MEAL_WINDOW, ""));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, true)), reasons, Collections.emptyList(),
                true, true, TravelEstimateQuality.ROUTED));

        PreviewRowView row = viewModel.getState().getPreviewRows().get(0);
        assertEquals("closes at 17:00", row.getReason(),
                "the constraint the traveller cannot change should be shown first");
        assertEquals(Arrays.asList("closes at 17:00", "a usual mealtime"), row.getAllReasons());
    }

    @Test
    void aLockIsTheMostImportantThingToSayAboutARow() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));
        List<Reason> reasons = Arrays.asList(
                new Reason("a", ReasonCode.IN_DAYLIGHT, ""),
                new Reason("a", ReasonCode.LOCKED_BY_USER, ""));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, false)), reasons, Collections.emptyList(),
                true, true, TravelEstimateQuality.ROUTED));

        assertEquals("you locked this time",
                viewModel.getState().getPreviewRows().get(0).getReason());
    }

    @Test
    void rowsWithoutReasonsSayNothingRatherThanPadding() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, true)), Collections.emptyList(),
                Collections.emptyList(), true, true, TravelEstimateQuality.ROUTED));

        assertEquals("", viewModel.getState().getPreviewRows().get(0).getReason());
    }

    @Test
    void travelRowsAreMarkedAsTravel() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(travelRow("a", 9, 20), activityRow("a", 10, true)),
                Collections.emptyList(), Collections.emptyList(), true, true,
                TravelEstimateQuality.ROUTED));

        assertEquals(PreviewRowView.Kind.TRAVEL,
                viewModel.getState().getPreviewRows().get(0).getKind());
        assertEquals(PreviewRowView.Kind.ACTIVITY,
                viewModel.getState().getPreviewRows().get(1).getKind());
    }

    @Test
    void anIncompleteSearchIsNotPresentedAsTheBestPossible() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, true)), Collections.emptyList(),
                Collections.emptyList(), true, false, TravelEstimateQuality.ROUTED));

        DayPlanState state = viewModel.getState();
        assertFalse(state.isSearchCompletedWithinLimit());
        assertTrue(state.getMessage().contains("within the search limit"));
    }

    @Test
    void unknownTravelQualityIsDisclosedRatherThanHidden() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, true)), Collections.emptyList(),
                Collections.emptyList(), true, true, TravelEstimateQuality.UNKNOWN));

        assertTrue(viewModel.getState().getTravelQualityNote().contains("may include estimates"));
    }

    @Test
    void routedTravelNeedsNoCaveat() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, true)), Collections.emptyList(),
                Collections.emptyList(), true, true, TravelEstimateQuality.ROUTED));

        assertEquals("", viewModel.getState().getTravelQualityNote());
    }

    @Test
    void theObjectiveSummaryNamesWhatWasOptimisedFor() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, true)), Collections.emptyList(),
                Collections.emptyList(), true, true, TravelEstimateQuality.ROUTED));

        String summary = viewModel.getState().getObjectiveSummary();
        assertTrue(summary.contains("less travel"));
        assertTrue(summary.contains("mealtimes"));
        assertTrue(summary.contains("daylight"));
        assertTrue(summary.contains("original order was kept"));
    }

    @Test
    void warningsSurviveIntoTheState() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));

        new AutoSchedulePresenter(viewModel).presentPreview(preview(
                Arrays.asList(activityRow("a", 10, true)), Collections.emptyList(),
                Arrays.asList("The forecast covers the whole day."), true, true,
                TravelEstimateQuality.ROUTED));

        assertEquals(1, viewModel.getState().getWarnings().size());
    }

    @Test
    void applyingReplacesTheItineraryAndKeepsActivityDetails() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9), existingEvent("b", 14));
        AutoSchedulePresenter presenter = new AutoSchedulePresenter(viewModel);

        presenter.presentApplied(new AutoScheduleAppliedOutputData("trip-1",
                Arrays.asList(activityRow("a", 10, true), travelRow("b", 11, 15),
                        activityRow("b", 12, true)),
                "fingerprint-2"));

        DayPlanState state = viewModel.getState();
        assertEquals(AutoScheduleStatus.APPLIED, state.getStatus());
        assertEquals(3, state.getEvents().size());
        assertEquals(LocalTime.of(10, 0), state.getEvents().get(0).getStartTime());
        assertNotNull(state.getEvents().get(0).getActivity(),
                "the Calendar still needs the real activity behind each event");
        assertEquals("Name of a", state.getEvents().get(0).getActivity().getName());
        assertEquals(EventType.TRAVEL, state.getEvents().get(1).getEventType());
        assertTrue(state.getPreviewRows().isEmpty(), "the proposal is finished with");
    }

    @Test
    void aConflictExplainsItselfAndLeavesTheItineraryAlone() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));
        List<ScheduledEvent> before = viewModel.getState().getEvents();

        new AutoSchedulePresenter(viewModel).presentConflict(new AutoScheduleConflictOutputData(
                ScheduleConflict.activityCannotFit("a", "Royal Ontario Museum", 90, 45)));

        DayPlanState state = viewModel.getState();
        assertEquals(AutoScheduleStatus.CONFLICT, state.getStatus());
        assertTrue(state.isError());
        assertTrue(state.getMessage().contains("Royal Ontario Museum"));
        assertTrue(state.getMessage().contains("90 minutes"));
        assertTrue(state.getMessage().contains("not changed"));
        assertEquals(before, state.getEvents());
    }

    @Test
    void eachConflictKindGetsItsOwnExplanation() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));
        AutoSchedulePresenter presenter = new AutoSchedulePresenter(viewModel);
        List<String> messages = new ArrayList<>();

        for (ScheduleConflict.Kind kind : ScheduleConflict.Kind.values()) {
            presenter.presentConflict(new AutoScheduleConflictOutputData(
                    ScheduleConflict.of(kind, "a", "Museum")));
            messages.add(viewModel.getState().getMessage());
        }

        assertEquals(ScheduleConflict.Kind.values().length,
                messages.stream().distinct().count(),
                "every kind of conflict should read differently");
    }

    @Test
    void aFailureIsShownAsAnErrorWithoutLosingTheItinerary() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));
        List<ScheduledEvent> before = viewModel.getState().getEvents();

        new AutoSchedulePresenter(viewModel).presentFailure("Trip not found");

        DayPlanState state = viewModel.getState();
        assertEquals(AutoScheduleStatus.FAILURE, state.getStatus());
        assertTrue(state.isError());
        assertEquals("Trip not found", state.getMessage());
        assertEquals(before, state.getEvents());
    }

    @Test
    void locksSurviveEveryOutcome() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));
        viewModel.setState(viewModel.getState().withLocks(
                new java.util.LinkedHashSet<>(Arrays.asList("a"))));
        AutoSchedulePresenter presenter = new AutoSchedulePresenter(viewModel);

        presenter.presentPreview(preview(Arrays.asList(activityRow("a", 10, true)),
                Collections.emptyList(), Collections.emptyList(), true, true,
                TravelEstimateQuality.ROUTED));
        assertTrue(viewModel.getState().getLockedEventIds().contains("a"));

        presenter.presentConflict(new AutoScheduleConflictOutputData(
                ScheduleConflict.noFeasibleOrder()));
        assertTrue(viewModel.getState().getLockedEventIds().contains("a"));

        presenter.presentFailure("something went wrong");
        assertTrue(viewModel.getState().getLockedEventIds().contains("a"));
    }

    @Test
    void observersAreNotifiedOfEveryOutcome() {
        DayPlanViewModel viewModel = viewModelWith(existingEvent("a", 9));
        List<String> notifications = new ArrayList<>();
        viewModel.addPropertyChangeListener(event -> notifications.add(event.getPropertyName()));
        AutoSchedulePresenter presenter = new AutoSchedulePresenter(viewModel);

        presenter.presentPreview(preview(Arrays.asList(activityRow("a", 10, true)),
                Collections.emptyList(), Collections.emptyList(), true, true,
                TravelEstimateQuality.ROUTED));
        presenter.presentApplied(new AutoScheduleAppliedOutputData("trip-1",
                Arrays.asList(activityRow("a", 10, true)), "fingerprint-2"));

        assertEquals(2, notifications.size(), "the Calendar depends on these notifications");
        assertSame("state", notifications.get(0));
    }
}
