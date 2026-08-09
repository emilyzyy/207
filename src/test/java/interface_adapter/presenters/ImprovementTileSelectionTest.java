package interface_adapter.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.ImprovementView;
import use_case.autoschedule.AutoScheduleInputData;
import use_case.autoschedule.AutoScheduleInteractor;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import use_case.autoschedule.testdoubles.FakeWeatherContextGateway;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Which explanations become tiles, and in what order.
 *
 * <p>A tile is a claim, and a grid of them is a claim about what mattered. So the ordering is
 * a presentation judgement made once, in the Presenter: a measurable saving beats a
 * constraint honoured, which beats a preference that happened to improve, which beats an
 * explanation of something that did not change. The View shows the strongest few and puts the
 * rest under the disclosure — it does not decide which are strongest.</p>
 */
class ImprovementTileSelectionTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);
    private static final List<SoftPolicy> POLICIES = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private static Activity place(String id, String name, ActivityCategory category,
                                  IndoorOutdoorType kind, double latitude, double longitude,
                                  LocalTime open, LocalTime close) {
        return new Activity(id, name, category, new Location(latitude, longitude, id), 4.5, 60,
                open, close, kind, "none");
    }

    private static ScheduledEvent event(String id, Activity activity, LocalTime start) {
        return new ScheduledEvent(id, activity, start, start.plusMinutes(60),
                EventType.ACTIVITY, "");
    }

    private static DayPlanState preview(Trip trip, FakeTravelTimeEstimator estimator,
                                        Set<String> locks, boolean keepOrder) {
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(trip.getId(),
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        new AutoScheduleInteractor(new FakeTripRepository(trip), estimator,
                new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                POLICIES, new ScheduleEngine())
                .preview(new AutoScheduleInputData(trip.getId(), LocalTime.of(9, 0),
                        LocalTime.of(21, 0), TransportationMode.WALKING, locks,
                        Collections.emptyList(), keepOrder, true));
        return viewModel.getState();
    }

    /** A day that wastes a lot of time and a lot of walking, so the savings are real. */
    private static Trip wastefulDay() {
        Activity far = place("far", "Far Museum", ActivityCategory.MUSEUM,
                IndoorOutdoorType.INDOOR, 43.80, -79.20, LocalTime.of(9, 0), LocalTime.of(20, 0));
        Activity near = place("near", "Near Museum", ActivityCategory.MUSEUM,
                IndoorOutdoorType.INDOOR, 43.65, -79.38, LocalTime.of(9, 0), LocalTime.of(20, 0));
        Activity alsoNear = place("also", "Also Near", ActivityCategory.MUSEUM,
                IndoorOutdoorType.INDOOR, 43.66, -79.39, LocalTime.of(9, 0), LocalTime.of(20, 0));

        Trip trip = new Trip("trip-1", "Toronto", DATE, LocalTime.of(9, 0), LocalTime.of(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(
                event("e-near", near, LocalTime.of(9, 0)),
                event("e-far", far, LocalTime.of(13, 0)),
                event("e-also", alsoNear, LocalTime.of(18, 0))));
        return trip;
    }

    private static FakeTravelTimeEstimator estimator() {
        FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(false);
        estimator.route("near", "also", 5).route("also", "near", 5)
                .route("near", "far", 40).route("far", "near", 40)
                .route("also", "far", 38).route("far", "also", 38);
        return estimator;
    }

    private static List<String> primaries(DayPlanState state) {
        List<String> shown = new ArrayList<>();
        for (ImprovementView card : state.getImprovements()) {
            shown.add(card.getPrimary());
        }
        return shown;
    }

    private static ImprovementView cardWith(DayPlanState state, String primary) {
        for (ImprovementView card : state.getImprovements()) {
            if (card.getPrimary().equals(primary)) {
                return card;
            }
        }
        return null;
    }

    @Test
    void aMeasurableSavingIsShownAsAFigureAndItsUnit() {
        DayPlanState state = preview(wastefulDay(), estimator(), Collections.emptySet(), false);
        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());

        boolean sawFigure = false;
        for (ImprovementView card : state.getImprovements()) {
            if (card.getPrimary().endsWith(" MIN")) {
                sawFigure = true;
                assertTrue(card.getSecondary().equals("less travel")
                                || card.getSecondary().equals("waiting removed"),
                        "a figure needs its unit: " + card.spoken());
            }
        }
        assertTrue(sawFigure, "this day saves real minutes: " + primaries(state));
    }

    /** Measurable savings lead; explanations of what did not change come last. */
    @Test
    void measurableSavingsAreRankedAheadOfExplanations() {
        DayPlanState state = preview(wastefulDay(), estimator(), Collections.emptySet(), true);
        List<String> shown = primaries(state);

        int firstFigure = -1;
        int orderKept = shown.indexOf("ORDER KEPT");
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).endsWith(" MIN")) {
                firstFigure = i;
                break;
            }
        }
        if (firstFigure >= 0 && orderKept >= 0) {
            assertTrue(firstFigure < orderKept,
                    "a saving the traveller can count beats a statement that nothing moved: "
                            + shown);
        }
    }

    /** A pinned activity names itself, so the tile says which one was honoured. */
    @Test
    void aHonouredPinNamesTheActivityItKept() {
        Trip trip = wastefulDay();
        DayPlanState state = preview(trip, estimator(),
                Collections.singleton("e-near"), false);

        ImprovementView pin = cardWith(state, "PIN KEPT");
        assertNotNull(pin, "the pin was honoured, so it should be reported: "
                + primaries(state));
        assertEquals("Near Museum", pin.getSecondary(),
                "and it should say which activity it kept");
    }

    /**
     * The order tile deliberately carries no supporting line. "Nothing was reordered" only
     * says "order kept" a second time, and a tile that repeats itself is one the eye learns
     * to skip.
     */
    @Test
    void theOrderTileDoesNotRestateItself() {
        DayPlanState state = preview(wastefulDay(), estimator(), Collections.emptySet(), true);

        ImprovementView order = cardWith(state, "ORDER KEPT");
        if (order != null) {
            assertEquals("", order.getSecondary(),
                    "the supporting line would only repeat the heading");
        }
    }

    /**
     * A preference being switched on is not an achievement. Weather and daylight are enabled
     * here and nothing about this indoor day improves on either, so neither may appear.
     */
    @Test
    void aPreferenceThatChangedNothingProducesNoTile() {
        DayPlanState state = preview(wastefulDay(), estimator(), Collections.emptySet(), false);
        List<String> shown = primaries(state);

        assertFalse(shown.contains("WEATHER IMPROVED"),
                "nothing here got better weather: " + shown);
        assertFalse(shown.contains("DAYLIGHT"),
                "every activity here is indoors: " + shown);
    }

    /** A day with nothing to show says so rather than inventing something. */
    @Test
    void aDayThatImprovesNothingProducesNoTiles() {
        Activity only = place("solo", "Solo Museum", ActivityCategory.MUSEUM,
                IndoorOutdoorType.INDOOR, 43.65, -79.38, LocalTime.of(9, 0), LocalTime.of(20, 0));
        Trip trip = new Trip("trip-1", "Toronto", DATE, LocalTime.of(9, 0), LocalTime.of(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Collections.singletonList(event("e-solo", only, LocalTime.of(9, 0))));

        DayPlanState state = preview(trip, estimator(), Collections.emptySet(), false);

        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        assertTrue(state.getImprovements().isEmpty(),
                "one activity that did not move improved nothing: " + primaries(state));
    }

    /** Every tile must be backed by the figures printed above it. */
    @Test
    void everyFigureTileAgreesWithTheReportedMetrics() {
        DayPlanState state = preview(wastefulDay(), estimator(), Collections.emptySet(), false);

        for (ImprovementView card : state.getImprovements()) {
            if ("less travel".equals(card.getSecondary())) {
                int saved = state.getMetrics().getTravelBeforeMinutes()
                        - state.getMetrics().getTravelAfterMinutes();
                assertEquals(saved + " MIN", card.getPrimary(),
                        "the tile must be Before minus Proposed, not a separate sum");
            }
            if ("waiting removed".equals(card.getSecondary())) {
                int saved = state.getMetrics().getIdleBeforeMinutes()
                        - state.getMetrics().getIdleAfterMinutes();
                assertEquals(saved + " MIN", card.getPrimary(),
                        "the tile must be Before minus Proposed, not a separate sum");
            }
        }
    }

    /** Running Preview twice replaces the tiles rather than adding a second set. */
    @Test
    void repeatedPreviewsReplaceTheTilesRatherThanAccumulateThem() {
        Trip trip = wastefulDay();
        FakeTripRepository trips = new FakeTripRepository(trip);
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(trip.getId(),
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        AutoScheduleInputData request = new AutoScheduleInputData("trip-1", LocalTime.of(9, 0),
                LocalTime.of(21, 0), TransportationMode.WALKING, Collections.emptySet(),
                Collections.emptyList(), false, true);

        AutoScheduleInteractor interactor = new AutoScheduleInteractor(trips, estimator(),
                new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                POLICIES, new ScheduleEngine());
        interactor.preview(request);
        int first = viewModel.getState().getImprovements().size();
        interactor.preview(request);

        assertEquals(first, viewModel.getState().getImprovements().size(),
                "a second look at the same day is the same set of claims, not twice as many");
    }
}
