package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import entity.entities.Activity;
import entity.entities.Trip;
import entity.entities.ScheduledEvent;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import interface_adapter.controllers.AutoScheduleController;
import interface_adapter.controllers.ManualPlanController;
import interface_adapter.controllers.TaskRunner;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.PreviewMetricsView;
import interface_adapter.viewmodels.PreviewRowView;
import use_case.autoschedule.AutoScheduleApplyInputData;
import use_case.autoschedule.AutoScheduleInputBoundary;
import use_case.autoschedule.AutoScheduleInputData;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.WeatherOption;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import java.time.LocalDate;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * A Preview is an unsaved draft, and the row controls must not be a way around that.
 *
 * <p>Reported from the running application: with a Preview open, pressing Remove on a proposed
 * activity left Preview and the schedule appeared to have been applied without Apply ever being
 * pressed. The cards on screen during a Preview are the proposal, but the buttons on them drove
 * the saved-plan use cases, so Remove wrote straight through to the repository and the presenter
 * replaced the Preview with the mutated saved day.</p>
 *
 * <p>Until removing from the <em>proposal</em> exists as an application-layer operation, these
 * controls are inert during a Preview. Inert is recoverable; silently rewriting someone's
 * itinerary is not.</p>
 */
class PreviewCannotMutateTheSavedPlanTest {

    /**
     * A real controller over a real in-memory repository.
     *
     * <p>Stronger than recording calls: the assertion becomes "the stored trip is byte-for-byte
     * what it was", which is the actual promise a Preview makes.</p>
     */
    private static ManualPlanController controllerOver(FakeTripRepository trips,
                                                       DayPlanViewModel dayPlan) {
        return new ManualPlanController(
                new use_case.usecases.AddActivityToPlanUseCase(trips,
                        new database.persistence.CachedPlacesRepository()),
                new use_case.usecases.EditScheduledEventUseCase(trips),
                new use_case.usecases.RemoveScheduledEventUseCase(trips),
                () -> "trip-1",
                new interface_adapter.presenters.ManualPlanPresenter(dayPlan,
                        new interface_adapter.viewmodels.SearchViewModel(
                                new interface_adapter.viewmodels.SearchState(
                                        Collections.emptyList(), ""))));
    }

    private static final AutoScheduleInputBoundary INERT = new AutoScheduleInputBoundary() {
        @Override
        public void preview(AutoScheduleInputData inputData) {
        }

        @Override
        public void apply(AutoScheduleApplyInputData inputData) {
        }

        @Override
        public WeatherOption weatherOptionFor(String tripId) {
            return WeatherOption.available();
        }

        @Override
        public void removeFromProposal(use_case.autoschedule.ProposalEditInputData inputData) {
        }
    };

    private static Trip tripWith(List<ScheduledEvent> events) {
        Trip trip = new Trip("trip-1", "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        trip.replaceSchedule(events);
        return trip;
    }

    private static int savedActivityCount(FakeTripRepository trips) {
        int count = 0;
        for (ScheduledEvent event : trips.findById("trip-1").get().getScheduledEvents()) {
            if (event.getEventType() == EventType.ACTIVITY) {
                count++;
            }
        }
        return count;
    }

    private static ScheduledEvent event(String id, int hour) {
        Activity activity = new Activity(id, id, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(8, 0), LocalTime.of(21, 0), IndoorOutdoorType.INDOOR, "none");
        return new ScheduledEvent(id, activity, LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0), EventType.ACTIVITY, "");
    }

    private static DayPlanState previewOver(List<ScheduledEvent> saved) {
        List<PreviewRowView> rows = new ArrayList<>();
        for (ScheduledEvent saveable : saved) {
            rows.add(new PreviewRowView(saveable.getId(), saveable.getActivity().getName(),
                    PreviewRowView.Kind.ACTIVITY, LocalTime.of(14, 0), LocalTime.of(15, 0),
                    false, true, "moved", Collections.singletonList("moved")));
        }
        return new DayPlanState("trip-1", saved, "Proposed schedule", false,
                Collections.emptyList(), AutoScheduleStatus.PREVIEW, rows,
                new PreviewMetricsView(30, 20, 90, 10, rows.size(), rows.size(), 120),
                Collections.emptyList(), "Arranged for less travel", true, true, "",
                "fingerprint", Collections.emptySet());
    }

    private static DayPlanPanel panelFor(DayPlanViewModel viewModel,
                                         ManualPlanController manual) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "these components need a display");
        final DayPlanPanel[] built = new DayPlanPanel[1];
        SwingUtilities.invokeAndWait(() -> built[0] = new DayPlanPanel(viewModel,
                new AutoScheduleController(INERT, viewModel, TaskRunner.immediate()), manual));
        return built[0];
    }

    private static List<AbstractButton> buttonsLabelled(Component root, String text) {
        List<AbstractButton> found = new ArrayList<>();
        collect(root, text, found);
        return found;
    }

    private static void collect(Component component, String text, List<AbstractButton> into) {
        if (component instanceof AbstractButton
                && text.equals(((AbstractButton) component).getText())) {
            into.add((AbstractButton) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, text, into);
            }
        }
    }

    @Test
    void removeEditsTheProposalAndNeverTheSavedPlan() throws Exception {
        List<ScheduledEvent> saved = List.of(event("a", 9), event("b", 12), event("c", 15));
        FakeTripRepository trips = new FakeTripRepository(tripWith(saved));
        DayPlanViewModel viewModel = new DayPlanViewModel(previewOver(saved));
        DayPlanPanel panel = panelFor(viewModel, controllerOver(trips, viewModel));

        List<AbstractButton> removes = buttonsLabelled(panel, "Remove");
        assertFalse(removes.isEmpty(), "the proposed rows should still show their controls");
        for (AbstractButton remove : removes) {
            assertTrue(remove.isEnabled(),
                    "Remove edits the proposal during a Preview, so it stays available");
        }

        // Pressing it must reach the draft, never the saved plan.
        SwingUtilities.invokeAndWait(() -> removes.forEach(AbstractButton::doClick));

        assertEquals(3, savedActivityCount(trips),
                "the stored trip must be exactly as it was before the Preview opened");
        assertEquals(AutoScheduleStatus.PREVIEW, viewModel.getState().getStatus(),
                "and the Preview must still be on screen");
    }

    @Test
    void editIsInertWhileAProposalIsOnScreen() throws Exception {
        List<ScheduledEvent> saved = List.of(event("a", 9), event("b", 12));
        FakeTripRepository trips = new FakeTripRepository(tripWith(saved));
        DayPlanViewModel viewModel = new DayPlanViewModel(previewOver(saved));
        DayPlanPanel panel = panelFor(viewModel, controllerOver(trips, viewModel));

        List<AbstractButton> edits = buttonsLabelled(panel, "Edit");
        assertFalse(edits.isEmpty());
        for (AbstractButton edit : edits) {
            assertFalse(edit.isEnabled(), "Edit must not act on a proposal either");
        }
    }

    /** The saved day is what the controls are for, once there is no proposal in the way. */
    @Test
    void theSameControlsWorkNormallyOnTheSavedDayPlan() throws Exception {
        List<ScheduledEvent> saved = List.of(event("a", 9), event("b", 12));
        FakeTripRepository trips = new FakeTripRepository(tripWith(saved));
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("trip-1", saved,
                "", false, Collections.emptyList()));
        DayPlanPanel panel = panelFor(viewModel, controllerOver(trips, viewModel));

        List<AbstractButton> removes = buttonsLabelled(panel, "Remove");
        assertFalse(removes.isEmpty());
        for (AbstractButton remove : removes) {
            assertTrue(remove.isEnabled(),
                    "with no proposal on screen these are the saved plan's own controls");
        }
    }

    /** Opening a Preview over a day whose controls were live must disarm them. */
    @Test
    void editIsDisarmedTheMomentAPreviewOpensAndRemoveStaysHarmless() throws Exception {
        List<ScheduledEvent> saved = List.of(event("a", 9), event("b", 12));
        FakeTripRepository trips = new FakeTripRepository(tripWith(saved));
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("trip-1", saved,
                "", false, Collections.emptyList()));
        DayPlanPanel panel = panelFor(viewModel, controllerOver(trips, viewModel));
        assertTrue(buttonsLabelled(panel, "Remove").get(0).isEnabled(), "precondition");

        SwingUtilities.invokeAndWait(() -> viewModel.setState(previewOver(saved)));

        for (AbstractButton edit : buttonsLabelled(panel, "Edit")) {
            assertFalse(edit.isEnabled(),
                    "Edit has no draft-only form yet, so a Preview must disarm it");
        }
        SwingUtilities.invokeAndWait(() ->
                buttonsLabelled(panel, "Remove").forEach(AbstractButton::doClick));
        assertEquals(2, savedActivityCount(trips), "and the stored trip is untouched");
    }
}
