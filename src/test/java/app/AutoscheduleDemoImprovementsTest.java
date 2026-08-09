package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import interface_adapter.controllers.AutoScheduleController;
import interface_adapter.controllers.AutoScheduleSettings;
import interface_adapter.controllers.TaskRunner;
import interface_adapter.gateways.DistanceServiceTravelTimeEstimator;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.ImprovementView;
import interface_adapter.viewmodels.PreviewMetricsView;
import app.AppContainer;
import use_case.autoschedule.AutoScheduleInteractor;
import use_case.autoschedule.WeatherContext;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.WeatherSeverity;
import interface_adapter.mock.MockDistanceService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The seeded demo, end to end, asserting the exact improvements it produces.
 *
 * <p>Every number here comes from the real Interactor running on
 * {@link AutoscheduleDemoTrip}. Nothing is staged in the Presenter, and the deterministic
 * travel and forecast mean the day tells the same story on any machine and without a
 * network. If the scheduler's behaviour changes, this test is meant to fail — that is what
 * makes it evidence rather than decoration.</p>
 */
class AutoscheduleDemoImprovementsTest {

    private DayPlanViewModel viewModel;

    private DayPlanState runDemo() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = app.trips.save(AutoscheduleDemoTrip.inefficientDay());
        List<WeatherWarning> hourly = AutoscheduleDemoTrip.hourlyForecast();

        viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false, hourly));

        Map<Integer, WeatherSeverity> byHour = new HashMap<>();
        for (WeatherWarning warning : hourly) {
            byHour.put(warning.getTime().getHour(), warning.getSeverity());
        }

        AutoScheduleInteractor interactor = new AutoScheduleInteractor(app.trips,
                new DistanceServiceTravelTimeEstimator(new MockDistanceService()),
                anyTrip -> WeatherContext.hourly(byHour),
                new AutoSchedulePresenter(viewModel),
                Arrays.asList(new WeatherSuitabilityPolicy(), new MealWindowPolicy(),
                        new DaylightPolicy()),
                new ScheduleEngine());
        AutoScheduleController controller =
                new AutoScheduleController(interactor, viewModel, TaskRunner.immediate());

        controller.toggleLock("event-museum");
        controller.preview(new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                Collections.singletonList(new AutoScheduleSettings.Window(
                        AutoscheduleDemoTrip.UNAVAILABLE_FROM,
                        AutoscheduleDemoTrip.UNAVAILABLE_TO)),
                true, true));
        return viewModel.getState();
    }

    private static List<String> headlines(DayPlanState state) {
        List<String> headlines = new ArrayList<>();
        for (ImprovementView improvement : state.getImprovements()) {
            headlines.add(improvement.getHeadline());
        }
        return headlines;
    }

    private static String subjectOf(DayPlanState state, String headline) {
        for (ImprovementView improvement : state.getImprovements()) {
            if (improvement.getHeadline().equals(headline)) {
                return improvement.getDetail();
            }
        }
        return null;
    }

    @Test
    void theSeededDemoSchedulesFiveActivitiesAndHonoursTheUnavailablePeriod() {
        DayPlanState state = runDemo();

        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        PreviewMetricsView metrics = state.getMetrics();
        assertEquals(5, metrics.getActivityCount(), "the demo is a five-activity day");
        assertTrue(state.getPreviewRows().stream()
                        .noneMatch(row -> row.getStart().isBefore(
                                AutoscheduleDemoTrip.UNAVAILABLE_TO)
                                && row.getEnd().isAfter(AutoscheduleDemoTrip.UNAVAILABLE_FROM)),
                "nothing may sit inside the unavailable period, travel included");
    }

    @Test
    void theSeededDemoProducesExactlyTheImprovementsItCanProve() {
        DayPlanState state = runDemo();
        List<String> shown = headlines(state);

        // Six, and each is a before/after comparison the Interactor computed.
        assertTrue(shown.contains("63 min of waiting removed"), shown.toString());
        assertTrue(shown.contains("8 min less travel"), shown.toString());
        assertTrue(shown.contains("Pinned activity kept at its time"), shown.toString());
        assertTrue(shown.contains("Meal moved to a better time"), shown.toString());
        assertTrue(shown.contains("Moved into daylight"), shown.toString());
        assertTrue(shown.contains("Moved to better weather"), shown.toString());
        assertEquals(6, shown.size(), "no other card should appear: " + shown);
    }

    @Test
    void eachImprovementNamesTheActivityItIsAbout() {
        DayPlanState state = runDemo();

        assertEquals("Royal Ontario Museum",
                subjectOf(state, "Pinned activity kept at its time"));
        assertEquals("St Lawrence Market", subjectOf(state, "Meal moved to a better time"));
        assertEquals("High Park", subjectOf(state, "Moved into daylight"));
        assertEquals("High Park", subjectOf(state, "Moved to better weather"));
    }

    /**
     * The demo reorders four of five activities, so claiming the order survived would be
     * false. This is the card most likely to be driven by the preference flag by mistake,
     * and the preference is deliberately set to true in {@link #runDemo()}.
     */
    @Test
    void theDemoDoesNotClaimTheOrderWasPreservedBecauseItWasNot() {
        DayPlanState state = runDemo();

        assertTrue(state.isKeptCurrentOrder(), "the preference was asked for");
        assertFalse(headlines(state).contains("Your original order was kept"),
                "four of five activities moved, so the order plainly did not survive");
    }

    @Test
    void thePinnedActivityIsStillAtItsOriginalTime() {
        DayPlanState state = runDemo();

        assertTrue(state.getPreviewRows().stream()
                        .anyMatch(row -> "Royal Ontario Museum".equals(row.getTitle())
                                && row.getStart().equals(LocalTime.of(11, 0))
                                && row.isLocked()),
                "the museum was pinned at 11:00 and must not have moved: "
                        + state.getPreviewRows());
    }

    @Test
    void theOutdoorActivityLeavesTheEveningRainAndTheDarkness() {
        DayPlanState state = runDemo();

        LocalTime parkStart = state.getPreviewRows().stream()
                .filter(row -> "High Park".equals(row.getTitle()))
                .map(row -> row.getStart()).findFirst().orElse(null);

        assertNotNull(parkStart);
        assertTrue(parkStart.isBefore(LocalTime.of(18, 0)),
                "the forecast turns at 6pm and dark follows; High Park started at 7:30pm "
                        + "and must end up earlier, but was placed at " + parkStart);
    }

    @Test
    void theMealMovesOutOfTheMiddleOfTheAfternoon() {
        DayPlanState state = runDemo();

        LocalTime lunchStart = state.getPreviewRows().stream()
                .filter(row -> "St Lawrence Market".equals(row.getTitle()))
                .map(row -> row.getStart()).findFirst().orElse(null);

        assertNotNull(lunchStart);
        assertTrue(lunchStart.isBefore(LocalTime.of(15, 30)),
                "lunch was at 3:30pm and should move toward a customary window, but was "
                        + "placed at " + lunchStart);
    }

    /**
     * The before-travel figure is the one the metric fix exists for: a hand-built plan has
     * no travel rows, and reporting zero made Autoschedule look as though it invented
     * journeys it had only made visible.
     */
    @Test
    void beforeTravelReflectsTheJourneysTheOriginalOrderActuallyRequired() {
        DayPlanState state = runDemo();
        PreviewMetricsView metrics = state.getMetrics();

        assertTrue(metrics.getTravelBeforeMinutes() > 0,
                "five activities in five different places never cost zero travel");
        assertEquals(189, metrics.getTravelBeforeMinutes(),
                "the deterministic demo should report a stable before-travel figure");
    }
}
