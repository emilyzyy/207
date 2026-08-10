package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static use_case.autoschedule.ProblemFixtures.at;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.PreviewMetricsView;
import interface_adapter.viewmodels.PreviewRowView;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import use_case.autoschedule.testdoubles.FakeWeatherContextGateway;

/**
 * When the traveller sets out, and whether the Preview's figures describe the day it draws.
 *
 * <p>Both were reported from the running application in the same screenshot. A journey to a
 * restaurant that opens at 11:30 was drawn at 10:00, leaving an unexplained hour and a half
 * between arriving and the doors opening; and the same Preview reported no waiting at all
 * while an eighty-three minute hole sat on the timeline above the claim.</p>
 */
class JustInTimeDepartureTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    private static Activity place(String id, String name, ActivityCategory category,
                                  double latitude, double longitude,
                                  LocalTime opening, LocalTime closing) {
        return new Activity(id, name, category, new Location(latitude, longitude, id), 4.5, 60,
                opening, closing, IndoorOutdoorType.INDOOR, "none");
    }

    private static ScheduledEvent scheduled(String id, Activity activity, LocalTime start) {
        return new ScheduledEvent(id, activity, start, start.plusMinutes(60),
                EventType.ACTIVITY, "");
    }

    /** The reported day: something open all day, then a place that does not open until 11:30. */
    private static Trip reportedTrip() {
        final Activity opensEarly = place("early", "Galleria The Kitchen Express",
                ActivityCategory.SHOPPING, 43.65, -79.38, at(9, 0), at(21, 0));
        final Activity opensLate = place("late", "Four Brothers Pizza",
                ActivityCategory.FOOD, 43.66, -79.39, at(11, 30), at(22, 0));
        final Activity nearby = place("near", "Farm Boy",
                ActivityCategory.SHOPPING, 43.66, -79.39, at(8, 0), at(22, 0));

        final Trip trip = new Trip("trip-1", "Toronto", DATE, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(
                scheduled("early", opensEarly, at(9, 0)),
                scheduled("late", opensLate, at(13, 0)),
                scheduled("near", nearby, at(16, 0))));
        return trip;
    }

    private static DayPlanState previewOf(Trip trip, FakeTravelTimeEstimator estimator,
                                          boolean keepCurrentOrder) {
        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(trip.getId(),
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        new AutoScheduleInteractor(new FakeTripRepository(trip), estimator,
                new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                Arrays.asList(new WeatherSuitabilityPolicy(), new MealWindowPolicy(),
                        new DaylightPolicy()),
                new ScheduleEngine())
                .preview(new AutoScheduleInputData(trip.getId(), at(9, 0), at(21, 0),
                        TransportationMode.WALKING, Collections.emptySet(),
                        Collections.emptyList(), keepCurrentOrder, true));
        return viewModel.getState();
    }

    private static PreviewRowView rowTitled(DayPlanState state, PreviewRowView.Kind kind,
                                            String fragment) {
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == kind && row.getTitle().contains(fragment)) {
                return row;
            }
        }
        return null;
    }

    /**
     * Arriving early at a shut door buys nothing, so the journey belongs at the end of the
     * free time rather than the start of it.
     */
    @Test
    void travelToALateOpeningVenueIsScheduledToArriveAsItOpens() {
        final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(false);
        estimator.route("early", "late", 7).route("late", "early", 7)
                .route("late", "near", 4).route("near", "late", 4)
                .route("early", "near", 9).route("near", "early", 9);

        final DayPlanState state = previewOf(reportedTrip(), estimator, true);

        final PreviewRowView activity = rowTitled(state, PreviewRowView.Kind.ACTIVITY,
                "Four Brothers Pizza");
        final PreviewRowView travel = rowTitled(state, PreviewRowView.Kind.TRAVEL,
                "Travel to Four Brothers Pizza");
        assertNotNull(activity, "the late-opening activity should be scheduled");
        assertNotNull(travel, "its journey should be drawn");

        assertEquals(at(11, 30), activity.getStart(), "it cannot start before it opens");
        assertEquals(at(11, 30), travel.getEnd(),
                "the journey should land as the doors open, not an hour and a half early");
        assertEquals(at(11, 23), travel.getStart(),
                "seven minutes before, so the waiting sits where the traveller actually is");
    }

    @Test
    void preserveOrderStillMovesAManuallyTimedButUnlockedDestination() {
        final Activity first = place("first", "First stop", ActivityCategory.MUSEUM,
                43.65, -79.38, at(9, 0), at(21, 0));
        final Activity second = place("second", "Second stop", ActivityCategory.MUSEUM,
                43.66, -79.39, at(9, 0), at(21, 0));
        final Trip trip = new Trip("trip-preserved", "Toronto", DATE, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        // The 1:00 PM manual time is only an input to Autoschedule. Only "first" is pinned.
        trip.replaceSchedule(Arrays.asList(
                scheduled("first", first, at(9, 0)),
                scheduled("second", second, at(13, 0))));
        final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(false)
                .route("first", "second", 20).route("second", "first", 20);
        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(trip.getId(),
                trip.getScheduledEvents(), "", false, Collections.emptyList()));

        new AutoScheduleInteractor(new FakeTripRepository(trip), estimator,
                new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                Collections.emptyList(), new ScheduleEngine())
                .preview(new AutoScheduleInputData(trip.getId(), at(9, 0), at(21, 0),
                        TransportationMode.WALKING, Collections.singleton("first"),
                        Collections.singletonList(new TimeWindow(at(10, 30), at(13, 0))),
                        true, true));

        final DayPlanState state = viewModel.getState();
        final PreviewRowView travel = rowTitled(state, PreviewRowView.Kind.TRAVEL,
                "Travel to Second stop");
        final PreviewRowView destination = rowTitled(state, PreviewRowView.Kind.ACTIVITY,
                "Second stop");
        assertNotNull(travel);
        assertNotNull(destination);
        assertEquals(at(13, 0), travel.getStart());
        assertEquals(at(13, 20), destination.getStart(),
                "preserving order does not freeze an unlocked activity's manual time");
        assertEquals(travel.getEnd(), destination.getStart());
    }

    /**
     * The waiting has not disappeared — it moved to the near side of the journey — so the
     * figures must still own up to it.
     */
    @Test
    void reportedFiguresDescribeTheTimelineThatIsDrawn() {
        final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(false);
        estimator.route("early", "late", 7).route("late", "early", 7)
                .route("late", "near", 4).route("near", "late", 4)
                .route("early", "near", 9).route("near", "early", 9);

        final DayPlanState state = previewOf(reportedTrip(), estimator, true);

        assertEquals(travelDrawn(state), state.getMetrics().getTravelAfterMinutes(),
                "reported travel must be the travel on the timeline");
        assertEquals(gapsDrawn(state), state.getMetrics().getIdleAfterMinutes(),
                "reported waiting must be the waiting on the timeline: " + rowsOf(state));
        assertTrue(gapsDrawn(state) > 0,
                "this day genuinely keeps a gap, so the test is proving something");
    }

    /**
     * The same agreement across many shapes of day, so it cannot hold by coincidence on one.
     * A failure prints the schedule it happened on.
     */
    @Test
    void reportedFiguresDescribeTheTimelineAcrossManyRandomDays() {
        final Random random = new Random(4820253L);
        int checked = 0;

        for (int trial = 0; trial < 120; trial++) {
            final int count = 2 + random.nextInt(4);
            final List<Activity> places = new ArrayList<>();
            final List<ScheduledEvent> events = new ArrayList<>();
            LocalTime cursor = at(9, 0);
            for (int i = 0; i < count; i++) {
                final String id = "p" + i;
                final int opensAt = 8 + random.nextInt(5);
                final Activity activity = place(id, "Place " + i,
                        random.nextBoolean() ? ActivityCategory.FOOD : ActivityCategory.MUSEUM,
                        43.6 + i * 0.01, -79.4 + i * 0.01,
                        at(opensAt, 0), at(20, 0));
                places.add(activity);
                final LocalTime start = cursor.isBefore(at(opensAt, 0)) ? at(opensAt, 0) : cursor;
                events.add(scheduled(id, activity, start));
                cursor = start.plusMinutes(90);
            }

            final Trip trip = new Trip("trip-" + trial, "Toronto", DATE, at(9, 0), at(21, 0),
                    TransportationMode.WALKING);
            trip.replaceSchedule(events);

            final FakeTravelTimeEstimator estimator =
                    new FakeTravelTimeEstimator().timeSensitive(random.nextBoolean());
            for (Activity from : places) {
                for (Activity to : places) {
                    if (!from.getId().equals(to.getId())) {
                        estimator.route(from.getId(), to.getId(), random.nextInt(40));
                    }
                }
            }

            final DayPlanState state = previewOf(trip, estimator, random.nextBoolean());
            if (state.getPreviewRows().isEmpty()) {
                continue;
            }
            final String scenario = "trial " + trial + " rows " + rowsOf(state);
            assertEquals(travelDrawn(state), state.getMetrics().getTravelAfterMinutes(),
                    "travel figure disagrees with the timeline on " + scenario);
            assertEquals(gapsDrawn(state), state.getMetrics().getIdleAfterMinutes(),
                    "waiting figure disagrees with the timeline on " + scenario);
            checked++;
        }
        assertTrue(checked > 80, "expected most trials to produce a schedule, got " + checked);
    }

    /**
     * Every journey the traveller makes must arrive by the time it is needed and start no
     * earlier than the previous activity ended, whatever else the search decided.
     */
    @Test
    void everyJourneyLandsWhenItIsNeededAndNotBeforeTheTravellerIsFree() {
        final Random random = new Random(99117L);

        for (int trial = 0; trial < 80; trial++) {
            final int count = 2 + random.nextInt(3);
            final List<Activity> places = new ArrayList<>();
            final List<ScheduledEvent> events = new ArrayList<>();
            LocalTime cursor = at(9, 0);
            for (int i = 0; i < count; i++) {
                final String id = "q" + i;
                final int opensAt = 8 + random.nextInt(4);
                final Activity activity = place(id, "Q" + i, ActivityCategory.MUSEUM,
                        43.6 + i * 0.02, -79.4 + i * 0.02, at(opensAt, 0), at(21, 0));
                places.add(activity);
                final LocalTime start = cursor.isBefore(at(opensAt, 0)) ? at(opensAt, 0) : cursor;
                events.add(scheduled(id, activity, start));
                cursor = start.plusMinutes(90);
            }
            final Trip trip = new Trip("t" + trial, "Toronto", DATE, at(9, 0), at(21, 0),
                    TransportationMode.WALKING);
            trip.replaceSchedule(events);

            final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(false);
            for (Activity from : places) {
                for (Activity to : places) {
                    if (!from.getId().equals(to.getId())) {
                        estimator.route(from.getId(), to.getId(), 1 + random.nextInt(25));
                    }
                }
            }

            final DayPlanState state = previewOf(trip, estimator, random.nextBoolean());
            final List<PreviewRowView> rows = state.getPreviewRows();
            for (int i = 0; i < rows.size(); i++) {
                final PreviewRowView row = rows.get(i);
                if (row.getKind() != PreviewRowView.Kind.TRAVEL) {
                    continue;
                }
                assertTrue(i + 1 < rows.size(), "a journey must lead somewhere: " + rowsOf(state));
                assertEquals(rows.get(i + 1).getStart(), row.getEnd(),
                        "a journey must land exactly when its activity starts: " + rowsOf(state));
                assertTrue(i > 0, "a journey cannot open the day: " + rowsOf(state));
                assertTrue(!row.getStart().isBefore(rows.get(i - 1).getEnd()),
                        "nobody sets out before they are free: " + rowsOf(state));
            }
        }
    }

    private static int travelDrawn(DayPlanState state) {
        int total = 0;
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.TRAVEL) {
                total += minutes(row.getStart(), row.getEnd());
            }
        }
        return total;
    }

    private static int gapsDrawn(DayPlanState state) {
        int total = 0;
        LocalTime cursor = null;
        for (PreviewRowView row : state.getPreviewRows()) {
            if (cursor != null && row.getStart().isAfter(cursor)) {
                total += minutes(cursor, row.getStart());
            }
            if (cursor == null || row.getEnd().isAfter(cursor)) {
                cursor = row.getEnd();
            }
        }
        return total;
    }

    private static String rowsOf(DayPlanState state) {
        final StringBuilder text = new StringBuilder();
        for (PreviewRowView row : state.getPreviewRows()) {
            text.append('\n').append("  ").append(row.getStart()).append('-').append(row.getEnd())
                    .append(' ').append(row.getTitle());
        }
        final PreviewMetricsView metrics = state.getMetrics();
        if (metrics != null) {
            text.append("\n  reported travel=").append(metrics.getTravelAfterMinutes())
                    .append(" waiting=").append(metrics.getIdleAfterMinutes());
        }
        return text.toString();
    }

    private static int minutes(LocalTime from, LocalTime to) {
        return (to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }
}
