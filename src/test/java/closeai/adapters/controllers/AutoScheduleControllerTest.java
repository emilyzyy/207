package closeai.adapters.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.adapters.viewmodels.AutoScheduleStatus;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.PreviewRowView;
import closeai.application.autoschedule.AutoScheduleApplyInputData;
import closeai.application.autoschedule.AutoScheduleInputBoundary;
import closeai.application.autoschedule.AutoScheduleInputData;
import closeai.application.autoschedule.ProposedEventData;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutoScheduleControllerTest {

    /** Captures what the controller handed across the boundary. */
    private static final class RecordingUseCase implements AutoScheduleInputBoundary {
        private AutoScheduleInputData previewInput;
        private AutoScheduleApplyInputData applyInput;
        private int previewCalls;

        @Override
        public void preview(AutoScheduleInputData inputData) {
            previewInput = inputData;
            previewCalls++;
        }

        @Override
        public void apply(AutoScheduleApplyInputData inputData) {
            applyInput = inputData;
        }
    }

    private final RecordingUseCase useCase = new RecordingUseCase();

    private static DayPlanViewModel viewModel(String tripId) {
        return new DayPlanViewModel(
                new DayPlanState(tripId, Collections.emptyList(), "", false));
    }

    private static AutoScheduleSettings settings(boolean keepOrder,
                                                 AutoScheduleSettings.Window... windows) {
        return new AutoScheduleSettings(LocalTime.of(10, 0), LocalTime.of(18, 0),
                TransportationMode.TRANSIT, Arrays.asList(windows), keepOrder);
    }

    private AutoScheduleController controllerFor(DayPlanViewModel viewModel) {
        return new AutoScheduleController(useCase, viewModel, TaskRunner.immediate());
    }

    @Test
    void previewPassesThePlainSettingsStraightThrough() {
        DayPlanViewModel viewModel = viewModel("trip-1");

        controllerFor(viewModel).preview(settings(true,
                new AutoScheduleSettings.Window(LocalTime.of(12, 0), LocalTime.of(13, 0))));

        AutoScheduleInputData input = useCase.previewInput;
        assertNotNull(input);
        assertEquals("trip-1", input.getTripId());
        assertEquals(LocalTime.of(10, 0), input.getAvailableStart());
        assertEquals(LocalTime.of(18, 0), input.getAvailableEnd());
        assertEquals(TransportationMode.TRANSIT, input.getTransportationMode());
        assertTrue(input.isKeepCurrentOrder());
        assertEquals(1, input.getUnavailableWindows().size());
        assertEquals(LocalTime.of(12, 0), input.getUnavailableWindows().get(0).getStart());
    }

    @Test
    void previewCarriesThePinnedActivities() {
        DayPlanViewModel viewModel = viewModel("trip-1");
        AutoScheduleController controller = controllerFor(viewModel);
        controller.toggleLock("dinner");

        controller.preview(settings(true));

        assertEquals(new LinkedHashSet<>(Arrays.asList("dinner")),
                useCase.previewInput.getLockedEventIds());
    }

    @Test
    void turningTheOrderPreferenceOffIsPassedOn() {
        controllerFor(viewModel("trip-1")).preview(settings(false));

        assertFalse(useCase.previewInput.isKeepCurrentOrder());
    }

    @Test
    void previewShowsThatWorkIsUnderWay() {
        DayPlanViewModel viewModel = viewModel("trip-1");
        List<AutoScheduleStatus> seen = new ArrayList<>();
        viewModel.addPropertyChangeListener(event -> seen.add(viewModel.getState().getStatus()));

        controllerFor(viewModel).preview(settings(true));

        assertTrue(seen.contains(AutoScheduleStatus.LOADING),
                "the traveller should see that something is happening");
    }

    @Test
    void withoutATripThereIsNothingToArrange() {
        controllerFor(viewModel("")).preview(settings(true));

        assertNull(useCase.previewInput);
    }

    @Test
    void theSlowWorkIsHandedToTheRunnerRatherThanRunInline() {
        DayPlanViewModel viewModel = viewModel("trip-1");
        List<Runnable> deferred = new ArrayList<>();
        AutoScheduleController controller =
                new AutoScheduleController(useCase, viewModel, deferred::add);

        controller.preview(settings(true));

        assertEquals(0, useCase.previewCalls,
                "nothing should have run yet: the event thread must stay free");
        assertEquals(1, deferred.size());
        assertEquals(AutoScheduleStatus.LOADING, viewModel.getState().getStatus());

        deferred.get(0).run();
        assertEquals(1, useCase.previewCalls);
    }

    @Test
    void applySendsBackExactlyWhatIsOnScreen() {
        DayPlanViewModel viewModel = viewModel("trip-1");
        viewModel.setState(previewing(viewModel.getState()));

        controllerFor(viewModel).apply();

        AutoScheduleApplyInputData input = useCase.applyInput;
        assertNotNull(input);
        assertEquals("trip-1", input.getTripId());
        assertEquals("fingerprint-1", input.getExpectedFingerprint());
        assertEquals(2, input.getProposedEvents().size());
        ProposedEventData first = input.getProposedEvents().get(0);
        assertEquals("travel-a", first.getEventId());
        assertEquals(ProposedEventData.Kind.TRAVEL, first.getKind());
        assertEquals(ProposedEventData.Kind.ACTIVITY,
                input.getProposedEvents().get(1).getKind());
        assertEquals(LocalTime.of(10, 0), input.getProposedEvents().get(1).getStart());
    }

    @Test
    void applyWithNothingOnScreenDoesNothing() {
        controllerFor(viewModel("trip-1")).apply();

        assertNull(useCase.applyInput);
    }

    @Test
    void cancelDiscardsTheProposalWithoutTouchingTheUseCase() {
        DayPlanViewModel viewModel = viewModel("trip-1");
        viewModel.setState(previewing(viewModel.getState()));

        controllerFor(viewModel).cancel();

        assertNull(useCase.applyInput, "cancelling must never reach the use case");
        assertEquals(AutoScheduleStatus.IDLE, viewModel.getState().getStatus());
        assertTrue(viewModel.getState().getPreviewRows().isEmpty());
        assertTrue(viewModel.getState().getMessage().contains("unchanged"));
    }

    @Test
    void pinningAnActivityTogglesIt() {
        DayPlanViewModel viewModel = viewModel("trip-1");
        AutoScheduleController controller = controllerFor(viewModel);

        controller.toggleLock("museum");
        assertTrue(controller.isLocked("museum"));

        controller.toggleLock("museum");
        assertFalse(controller.isLocked("museum"));
    }

    @Test
    void pinsSurviveAcrossRepeatedRuns() {
        DayPlanViewModel viewModel = viewModel("trip-1");
        AutoScheduleController controller = controllerFor(viewModel);
        controller.toggleLock("museum");

        controller.preview(settings(true));
        controller.preview(settings(true));

        assertTrue(useCase.previewInput.getLockedEventIds().contains("museum"),
                "pins are remembered for as long as the app is open");
    }

    private static DayPlanState previewing(DayPlanState base) {
        List<PreviewRowView> rows = Arrays.asList(
                new PreviewRowView("travel-a", "Travel to Museum", PreviewRowView.Kind.TRAVEL,
                        LocalTime.of(9, 40), LocalTime.of(10, 0), false, false, "", null),
                new PreviewRowView("a", "Museum", PreviewRowView.Kind.ACTIVITY,
                        LocalTime.of(10, 0), LocalTime.of(11, 0), false, true, "closes at 17:00",
                        Arrays.asList("closes at 17:00")));
        return new DayPlanState(base.getTripId(), base.getEvents(), "proposed", false,
                AutoScheduleStatus.PREVIEW, rows, null, Collections.emptyList(), "", true, true,
                "", "fingerprint-1", base.getLockedEventIds());
    }
}
