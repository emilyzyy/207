package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.EventType;
import entity.valueobjects.TransportationMode;
import interface_adapter.controllers.AutoScheduleController;
import interface_adapter.controllers.AutoScheduleSettings;
import interface_adapter.controllers.ManualPlanController;
import interface_adapter.controllers.TaskRunner;
import interface_adapter.gateways.DistanceServiceTravelTimeEstimator;
import interface_adapter.gateways.WeatherServiceContextGateway;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.presenters.ManualPlanPresenter;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.PreviewRowView;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import use_case.autoschedule.AutoScheduleInteractor;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;

/**
 * The whole loop a traveller actually walks, driven through the real wiring.
 *
 * <p>Real container, real use cases, real controllers, real presenters, real view models, real
 * repository. Nothing is stubbed except the network, because the point is whether these pieces
 * still agree with each other after a dozen operations — not whether any one of them works.</p>
 *
 * <p>Most of the faults found in this feature have been of exactly this kind: each part correct
 * alone, and the day quietly wrong after the fourth or fifth thing the traveller did.</p>
 */
class RepeatedWorkflowAuditTest {

    private AppContainer app;
    private DayPlanViewModel dayPlan;
    private AutoScheduleController autoschedule;
    private ManualPlanController manual;
    private String tripId;

    @BeforeEach
    void buildTheRealThing() {
        app = new AppBuilder().buildOffline();
        Trip trip = app.trips.save(new Trip("t", "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING));
        tripId = trip.getId();
        List<Activity> pool = app.activities.findAll();
        for (int i = 0; i < 4; i++) {
            trip = app.addActivityToPlan.execute(tripId, pool.get(i).getId(),
                    LocalTime.of(9 + i * 3, 0));
        }

        dayPlan = new DayPlanViewModel(new DayPlanState(tripId, trip.getScheduledEvents(),
                "", false, Collections.emptyList()));
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        AutoSchedulePresenter presenter = new AutoSchedulePresenter(dayPlan);
        autoschedule = new AutoScheduleController(new AutoScheduleInteractor(app.trips,
                new DistanceServiceTravelTimeEstimator(app.distances, false),
                new WeatherServiceContextGateway(app.weather), presenter,
                Arrays.asList(new WeatherSuitabilityPolicy(), new MealWindowPolicy(),
                        new DaylightPolicy()),
                new ScheduleEngine()), dayPlan, TaskRunner.immediate());
        manual = new ManualPlanController(app.addActivityToPlan, app.editEvent, app.removeEvent,
                () -> tripId, new ManualPlanPresenter(dayPlan, search));
    }

    private void preview() {
        autoschedule.preview(new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                TransportationMode.WALKING, Collections.emptyList(), false, true,
                true, true, true, true));
    }

    private List<String> proposedActivities() {
        List<String> ids = new ArrayList<>();
        for (PreviewRowView row : dayPlan.getState().getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.ACTIVITY) {
                ids.add(row.getEventId());
            }
        }
        return ids;
    }

    private List<ScheduledEvent> saved() {
        return app.trips.findById(tripId).orElseThrow().getScheduledEvents();
    }

    private List<String> savedActivityIds() {
        List<String> ids = new ArrayList<>();
        for (ScheduledEvent event : saved()) {
            if (event.getEventType() == EventType.ACTIVITY) {
                ids.add(event.getId());
            }
        }
        return ids;
    }

    private String describe() {
        StringBuilder text = new StringBuilder("\nsaved:\n");
        for (ScheduledEvent event : saved()) {
            text.append("  ").append(event.getEventType()).append(' ')
                    .append(event.getStartTime()).append('-').append(event.getEndTime())
                    .append(' ').append(event.getId()).append('\n');
        }
        text.append("viewmodel status ").append(dayPlan.getState().getStatus()).append('\n');
        return text.toString();
    }

    /** No activity twice, no journey twice, no journey to somewhere that has gone. */
    private void assertCoherent() {
        List<String> activities = savedActivityIds();
        assertEquals(new HashSet<>(activities).size(), activities.size(),
                "an activity appears twice" + describe());
        Set<String> destinations = new HashSet<>();
        for (ScheduledEvent event : saved()) {
            if (event.getEventType() != EventType.TRAVEL) {
                continue;
            }
            String destination = event.getId().replaceFirst("^travel-", "");
            assertTrue(activities.contains(destination),
                    "orphaned journey " + event.getId() + describe());
            assertTrue(destinations.add(destination),
                    "duplicate journey " + event.getId() + describe());
        }
        // The Day Plan and the Calendar read the same state object, so agreement is structural
        // rather than coincidental; this pins that they are still the same list.
        assertEquals(saved().size(), dayPlan.getState().getEvents().size(),
                "the screen and the repository disagree" + describe());
    }

    @Test
    void theWholeLoopStaysCoherent() {
        assertEquals(4, savedActivityIds().size(), "four activities to begin with");

        // Preview -> draft removal -> Cancel: nothing may have been saved.
        preview();
        assertEquals(AutoScheduleStatus.PREVIEW, dayPlan.getState().getStatus());
        autoschedule.removeFromProposal(proposedActivities().get(1));
        assertEquals(AutoScheduleStatus.PREVIEW, dayPlan.getState().getStatus(),
                "a draft removal is not an Apply" + describe());
        assertEquals(4, savedActivityIds().size(), "Cancel has not even been pressed yet");
        autoschedule.cancel();
        assertEquals(4, savedActivityIds().size(), "Cancel restores nothing because nothing went");
        assertCoherent();

        // Preview -> draft removal -> Apply: exactly the edited proposal is saved.
        preview();
        String dropped = proposedActivities().get(1);
        autoschedule.removeFromProposal(dropped);
        List<String> intended = proposedActivities();
        autoschedule.apply();
        assertEquals(AutoScheduleStatus.APPLIED, dayPlan.getState().getStatus(), describe());
        assertEquals(new HashSet<>(intended), new HashSet<>(savedActivityIds()),
                "Apply must save exactly what was on screen" + describe());
        assertFalse(savedActivityIds().contains(dropped),
                "including the removal" + describe());
        assertCoherent();

        // Add one back, and schedule again. Generated travel must not become a task.
        Activity extra = app.activities.findAll().get(5);
        app.addActivityToPlan.execute(tripId, extra.getId(), LocalTime.of(19, 0));
        dayPlan.setState(new DayPlanState(tripId, saved(), "", false, Collections.emptyList()));
        preview();
        assertEquals(AutoScheduleStatus.PREVIEW, dayPlan.getState().getStatus(), describe());
        for (String id : proposedActivities()) {
            assertFalse(id.startsWith("travel-"),
                    "a generated journey was scheduled as an activity: " + id + describe());
        }
        assertEquals(4, proposedActivities().size(),
                "three that survived plus the new one" + describe());
        autoschedule.apply();
        assertCoherent();

        // Editing a saved activity, then scheduling again.
        String toEdit = savedActivityIds().get(0);
        manual.edit(toEdit, "09:00", "10:00", "");
        assertCoherent();
        preview();
        assertEquals(AutoScheduleStatus.PREVIEW, dayPlan.getState().getStatus(), describe());
        autoschedule.apply();
        assertCoherent();

        // Removing a saved activity, then scheduling again.
        manual.remove(savedActivityIds().get(0));
        assertCoherent();
        preview();
        autoschedule.apply();
        assertCoherent();
        assertEquals(3, savedActivityIds().size(), describe());
    }

    /** Applying twice must not double anything. */
    @Test
    void applyingTheSameProposalTwiceChangesNothingTheSecondTime() {
        preview();
        autoschedule.apply();
        List<String> afterFirst = savedActivityIds();
        int travelAfterFirst = 0;
        for (ScheduledEvent event : saved()) {
            if (event.getEventType() == EventType.TRAVEL) {
                travelAfterFirst++;
            }
        }

        autoschedule.apply();

        assertEquals(afterFirst, savedActivityIds(), "a second Apply is a no-op" + describe());
        int travelAfterSecond = 0;
        for (ScheduledEvent event : saved()) {
            if (event.getEventType() == EventType.TRAVEL) {
                travelAfterSecond++;
            }
        }
        assertEquals(travelAfterFirst, travelAfterSecond, "journeys must not double"
                + describe());
        assertCoherent();
    }

    /** Nothing about one attempt may survive into the next. */
    @Test
    void noPreviewStateLeaksIntoTheNextAttempt() {
        preview();
        autoschedule.removeFromProposal(proposedActivities().get(0));
        autoschedule.cancel();

        DayPlanState cancelled = dayPlan.getState();
        assertEquals(AutoScheduleStatus.IDLE, cancelled.getStatus());
        assertTrue(cancelled.getPreviewRows().isEmpty(), "no proposed rows survive");
        assertTrue(cancelled.getImprovements().isEmpty(), "no tiles survive");
        assertTrue(cancelled.getConstraintChips().isEmpty(), "no chips survive");
        assertEquals("", cancelled.getTradeOff(), "no trade-off survives");
        assertFalse(cancelled.hasBlockingNotice(), "no conflict survives");

        preview();
        assertEquals(4, proposedActivities().size(),
                "the next attempt starts from the saved day, not the abandoned draft"
                        + describe());
    }

    /** Switching trips must not carry one day's Preview onto another. */
    @Test
    void switchingTripsClearsEverythingAboutThePreviousPreview() {
        preview();
        assertEquals(AutoScheduleStatus.PREVIEW, dayPlan.getState().getStatus());

        Trip other = app.trips.save(new Trip("t2", "Montreal", LocalDate.of(2026, 8, 13),
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING));
        dayPlan.setState(new DayPlanState(other.getId(), other.getScheduledEvents(), "", false,
                Collections.emptyList()));

        DayPlanState now = dayPlan.getState();
        assertEquals(AutoScheduleStatus.IDLE, now.getStatus());
        assertTrue(now.getPreviewRows().isEmpty());
        assertTrue(now.getImprovements().isEmpty());
        assertTrue(now.getConstraintChips().isEmpty());
        assertEquals("t2", now.getTripId());
        assertNotNull(app.trips.findById(tripId).orElse(null),
                "and the first trip is still there, untouched");
        assertEquals(4, savedActivityIds().size());
    }

    /** Remembered settings belong to one day, not to the app. */
    @Test
    void rememberedSettingsAreKeptPerDayAndStayVisible() {
        AutoScheduleSettings withWindow = new AutoScheduleSettings(LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING,
                Collections.singletonList(new AutoScheduleSettings.Window(
                        LocalTime.of(10, 0), LocalTime.of(13, 0))),
                false, true, true, true, true, true);

        autoschedule.preview(withWindow);

        AutoScheduleSettings back = autoschedule.rememberedSettings();
        assertNotNull(back, "the next attempt should open on what was just used");
        assertEquals(1, back.getUnavailableWindows().size());
        assertEquals(LocalTime.of(10, 0), back.getUnavailableWindows().get(0).getStart());
        assertEquals(LocalTime.of(18, 0), back.getAvailableEnd());

        autoschedule.forgetRememberedSettings();
        assertEquals(null, autoschedule.rememberedSettings(),
                "and Reset must actually forget it");
    }
}
