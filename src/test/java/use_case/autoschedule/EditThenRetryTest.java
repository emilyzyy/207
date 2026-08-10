package use_case.autoschedule;

import static use_case.autoschedule.ProblemFixtures.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.OpeningHours;
import entity.valueobjects.TransportationMode;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.presenters.ManualPlanPresenter;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.PreviewRowView;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import use_case.autoschedule.testdoubles.FakeWeatherContextGateway;
import use_case.usecases.EditScheduledEventUseCase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Editing an activity and running Autoschedule again.
 *
 * <p>Reported from Venice on a Sunday: Autoschedule refused, the traveller moved the blocking
 * activity to a different time, ran it again, and got what looked like the identical error. It
 * was identical, and it was correct — {@code 40/60 Food Shop Take Away} is tagged
 * {@code Mo-Sa 07:45-16:00} and the trip was a Sunday, so no time of that day could ever have
 * worked. The message never said so, which made a true answer look like a stuck one.</p>
 *
 * <p>These tests hold both halves down: the retry really is a fresh run against the edited
 * plan, and the sentence explaining a refusal names the thing that actually blocked it.</p>
 */
class EditThenRetryTest {

    private static final LocalDate SUNDAY = LocalDate.of(2026, 8, 9);
    private static final List<use_case.autoschedule.policy.SoftPolicy> POLICIES = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private final DayPlanViewModel viewModel = new DayPlanViewModel(
            new DayPlanState("trip-venice", Collections.emptyList(), "", false,
                    Collections.emptyList()));
    private final FakeTravelTimeEstimator estimator =
            new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(6);
    private int interactorRuns;

    private static OpeningHours except(DayOfWeek closed, LocalTime open, LocalTime close) {
        Map<DayOfWeek, List<OpeningHours.TimeInterval>> week = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day != closed) {
                week.put(day, Collections.singletonList(
                        new OpeningHours.TimeInterval(open, close)));
            }
        }
        return OpeningHours.of(week);
    }

    private static OpeningHours everyDay(LocalTime open, LocalTime close) {
        Map<DayOfWeek, List<OpeningHours.TimeInterval>> week = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            week.put(day, Collections.singletonList(new OpeningHours.TimeInterval(open, close)));
        }
        return OpeningHours.of(week);
    }

    private static Activity place(String id, String name, OpeningHours hours,
                                  LocalTime open, LocalTime close) {
        return new Activity(id, name, ActivityCategory.FOOD, new Location(45.44, 12.33, id),
                4.5, 60, open, close, IndoorOutdoorType.INDOOR, "none", "hours", hours);
    }

    /** The reported day: one venue shut on Sundays, two open. */
    private static Trip veniceSunday() {
        Activity shutOnSunday = place("shop", "40/60 Food Shop Take Away",
                except(DayOfWeek.SUNDAY, at(7, 45), at(16, 0)), at(7, 45), at(16, 0));
        Activity anice = place("anice", "Anice Stellato",
                everyDay(at(10, 30), at(15, 0)), at(10, 30), at(15, 0));
        Activity macana = place("macana", "Ca Macana",
                everyDay(at(10, 0), at(19, 0)), at(10, 0), at(19, 0));

        Trip trip = new Trip("trip-venice", "Venice", SUNDAY, at(9, 0), at(18, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(
                new ScheduledEvent("e-shop", shutOnSunday, at(9, 0), at(10, 0),
                        EventType.ACTIVITY, ""),
                new ScheduledEvent("e-anice", anice, at(11, 45), at(12, 45),
                        EventType.ACTIVITY, ""),
                new ScheduledEvent("e-macana", macana, at(13, 0), at(14, 0),
                        EventType.ACTIVITY, "")));
        return trip;
    }

    /** A day everything is open for, so scheduling can succeed. */
    private static Trip openDay() {
        Activity one = place("a", "Anice Stellato", everyDay(at(9, 0), at(20, 0)),
                at(9, 0), at(20, 0));
        Activity two = place("b", "Ca Macana", everyDay(at(9, 0), at(20, 0)),
                at(9, 0), at(20, 0));
        Trip trip = new Trip("trip-venice", "Venice", SUNDAY, at(9, 0), at(18, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(
                new ScheduledEvent("e-a", one, at(9, 0), at(10, 0), EventType.ACTIVITY, ""),
                new ScheduledEvent("e-b", two, at(14, 0), at(15, 0), EventType.ACTIVITY, "")));
        return trip;
    }

    private void autoschedule(FakeTripRepository trips, boolean keepOrder) {
        interactorRuns++;
        new AutoScheduleInteractor(trips, estimator, new FakeWeatherContextGateway(),
                new AutoSchedulePresenter(viewModel), POLICIES, new ScheduleEngine())
                .preview(new AutoScheduleInputData("trip-venice", at(9, 0), at(18, 0),
                        TransportationMode.WALKING, Collections.emptySet(),
                        Collections.emptyList(), keepOrder, true));
    }

    /** What the running application does after a manual edit. */
    private void refreshAfterEdit(Trip saved) {
        new ManualPlanPresenter(viewModel,
                new SearchViewModel(new SearchState(Collections.emptyList(), "")))
                .presentSuccess(saved, "Scheduled event updated");
    }

    private static LocalTime startOf(Trip trip, String eventId) {
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getId().equals(eventId)) {
                return event.getStartTime();
            }
        }
        return null;
    }

    private static PreviewRowView row(DayPlanState state, String eventId) {
        for (PreviewRowView candidate : state.getPreviewRows()) {
            if (candidate.getKind() == PreviewRowView.Kind.ACTIVITY
                    && candidate.getEventId().equals(eventId)) {
                return candidate;
            }
        }
        return null;
    }

    // 1
    @Test
    void editingAnUnlockedActivityUpdatesTheRepository() {
        FakeTripRepository trips = new FakeTripRepository(veniceSunday());

        new EditScheduledEventUseCase(trips)
                .execute("trip-venice", "e-shop", at(14, 0), at(15, 0), "");

        assertEquals(at(14, 0), startOf(trips.findById("trip-venice").get(), "e-shop"),
                "the edited time must be what is stored");
    }

    // 2
    @Test
    void theNextAutoscheduleRequestSeesTheEditedEvent() {
        FakeTripRepository trips = new FakeTripRepository(openDay());
        Trip edited = new EditScheduledEventUseCase(trips)
                .execute("trip-venice", "e-b", at(16, 0), at(17, 0), "");
        refreshAfterEdit(edited);

        autoschedule(trips, true);

        // The proposal is built from the stored day, so the "before" figures describe the
        // edited plan rather than the one the traveller has already changed.
        assertEquals(AutoScheduleStatus.PREVIEW, viewModel.getState().getStatus(),
                viewModel.getState().getMessage());
        assertEquals(at(16, 0), startOf(trips.findById("trip-venice").get(), "e-b"));
    }

    // 3
    @Test
    void aPreviousConflictIsClearedBeforeTheNextAttempt() {
        FakeTripRepository trips = new FakeTripRepository(veniceSunday());
        autoschedule(trips, true);
        assertTrue(viewModel.getState().hasBlockingNotice(), "the fixture should conflict");

        Trip edited = new EditScheduledEventUseCase(trips)
                .execute("trip-venice", "e-shop", at(14, 0), at(15, 0), "");
        refreshAfterEdit(edited);

        assertFalse(viewModel.getState().hasBlockingNotice(),
                "editing the day must take the old refusal off the screen with it");
        assertFalse(viewModel.getState().isError());
    }

    // 4
    @Test
    void retryingAfterAnEditRunsTheUseCaseAgain() {
        FakeTripRepository trips = new FakeTripRepository(openDay());
        autoschedule(trips, true);
        String firstMessage = viewModel.getState().getMessage();

        Trip edited = new EditScheduledEventUseCase(trips)
                .execute("trip-venice", "e-b", at(11, 0), at(12, 0), "");
        refreshAfterEdit(edited);
        assertNotEquals(firstMessage, viewModel.getState().getMessage());

        autoschedule(trips, true);

        assertEquals(2, interactorRuns, "each press is its own run");
        assertEquals(AutoScheduleStatus.PREVIEW, viewModel.getState().getStatus(),
                "the second run must produce its own answer: "
                        + viewModel.getState().getMessage());
    }

    // 5
    @Test
    void anUnlockedActivityOriginalTimeIsNotTreatedAsFixed() {
        FakeTripRepository trips = new FakeTripRepository(openDay());

        autoschedule(trips, false);

        DayPlanState state = viewModel.getState();
        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        PreviewRowView moved = row(state, "e-b");
        assertNotNull(moved);
        assertNotEquals(at(14, 0), moved.getStart(),
                "an unlocked activity may be retimed; its current time is only the starting "
                        + "point, not a constraint");
        assertTrue(state.getLockedEventIds().isEmpty(),
                "nothing was pinned, so nothing may behave as pinned");
    }

    // 6
    @Test
    void anActivityPlacedTooEarlyCanBeMovedLater() {
        Activity opensLate = place("late", "Opens at noon", everyDay(at(12, 0), at(20, 0)),
                at(12, 0), at(20, 0));
        // A second activity so the day has something to arrange; with only the late venue
        // nothing can move and Autoschedule now declines rather than proposing a copy.
        Activity openAllDay = place("early", "Open all day", everyDay(at(8, 0), at(20, 0)),
                at(8, 0), at(20, 0));
        Trip trip = new Trip("trip-venice", "Venice", SUNDAY, at(9, 0), at(18, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(
                new ScheduledEvent("e-early", openAllDay, at(14, 0), at(15, 0),
                        EventType.ACTIVITY, ""),
                new ScheduledEvent("e-late", opensLate, at(16, 0), at(17, 0),
                        EventType.ACTIVITY, "")));
        FakeTripRepository trips = new FakeTripRepository(trip);

        autoschedule(trips, false);

        PreviewRowView placed = row(viewModel.getState(), "e-late");
        assertNotNull(placed, viewModel.getState().getMessage());
        assertTrue(!placed.getStart().isBefore(at(12, 0)),
                "it cannot be scheduled before it opens");
    }

    // 7
    @Test
    void anActivityPlacedTooLateCanBeMovedEarlier() {
        Activity closesEarly = place("early", "Shuts at noon", everyDay(at(9, 0), at(12, 0)),
                at(9, 0), at(12, 0));
        Trip trip = new Trip("trip-venice", "Venice", SUNDAY, at(9, 0), at(18, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Collections.singletonList(
                new ScheduledEvent("e-early", closesEarly, at(11, 0), at(12, 0),
                        EventType.ACTIVITY, "")));
        FakeTripRepository trips = new FakeTripRepository(trip);

        autoschedule(trips, false);

        PreviewRowView placed = row(viewModel.getState(), "e-early");
        assertNotNull(placed, viewModel.getState().getMessage());
        assertTrue(!placed.getEnd().isAfter(at(12, 0)), "it must finish before it shuts");
        assertEquals(at(9, 0), placed.getStart(), "and it is free to move earlier to do it");
    }

    // 8
    @Test
    void preserveOrderKeepsTheSequenceButNotTheExactStartTimes() {
        FakeTripRepository trips = new FakeTripRepository(openDay());

        autoschedule(trips, true);

        DayPlanState state = viewModel.getState();
        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        List<String> order = new ArrayList<>();
        for (PreviewRowView candidate : state.getPreviewRows()) {
            if (candidate.getKind() == PreviewRowView.Kind.ACTIVITY) {
                order.add(candidate.getEventId());
            }
        }
        assertEquals(Arrays.asList("e-a", "e-b"), order, "the sequence is what is preserved");
        assertNotEquals(at(14, 0), row(state, "e-b").getStart(),
                "the clock is not; keeping order must not freeze the times");
    }

    // 9
    @Test
    void aVenueClosedOnTheTripDateStillRefusesAndSaysWhy() {
        FakeTripRepository trips = new FakeTripRepository(veniceSunday());

        autoschedule(trips, true);

        DayPlanState state = viewModel.getState();
        assertEquals(AutoScheduleStatus.CONFLICT, state.getStatus());
        assertTrue(state.getMessage().contains("40/60 Food Shop Take Away"),
                "the message names the venue: " + state.getMessage());
        assertTrue(state.getMessage().contains("closed on Sundays"),
                "and says it is shut that day rather than that zero minutes fit: "
                        + state.getMessage());
        assertFalse(state.getMessage().contains("only 0"),
                "the old arithmetic phrasing invited the traveller to keep retiming it: "
                        + state.getMessage());
    }

    // 10
    @Test
    void aSuccessfulRetryRemovesThePreviousErrorMessage() {
        FakeTripRepository trips = new FakeTripRepository(veniceSunday());
        autoschedule(trips, true);
        assertTrue(viewModel.getState().hasBlockingNotice());

        // Remove the venue that cannot be scheduled, which is what the message advises.
        Trip trip = trips.findById("trip-venice").get();
        List<ScheduledEvent> remaining = new ArrayList<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (!event.getId().equals("e-shop")) {
                remaining.add(event);
            }
        }
        trip.replaceSchedule(remaining);
        trips.save(trip);

        autoschedule(trips, true);

        DayPlanState state = viewModel.getState();
        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        assertFalse(state.hasBlockingNotice(), "a working proposal clears the old refusal");
        assertFalse(state.isError());
    }

    // 11
    @Test
    void repeatedFailuresReplaceTheMessageRatherThanStackingIt() {
        FakeTripRepository trips = new FakeTripRepository(veniceSunday());

        autoschedule(trips, true);
        String first = viewModel.getState().getMessage();
        autoschedule(trips, true);
        String second = viewModel.getState().getMessage();

        assertEquals(first, second, "the same real conflict says the same thing");
        assertEquals(1, countOccurrences(second, "40/60 Food Shop Take Away"),
                "and says it once, not once per attempt: " + second);
    }

    // 12
    @Test
    void dismissingTheNoticeLeavesTheDayAloneAndAllowsAnotherAttempt() {
        FakeTripRepository trips = new FakeTripRepository(veniceSunday());
        autoschedule(trips, true);
        List<ScheduledEvent> dayBefore = viewModel.getState().getEvents();

        viewModel.setState(viewModel.getState().withoutNotice());

        DayPlanState dismissed = viewModel.getState();
        assertFalse(dismissed.hasBlockingNotice(), "OK takes the message away");
        assertEquals("", dismissed.getMessage());
        assertFalse(dismissed.isError());
        assertEquals(AutoScheduleStatus.IDLE, dismissed.getStatus(),
                "and leaves Autoschedule available to run again");
        assertEquals(dayBefore, dismissed.getEvents(),
                "OK dismisses a message; it must not touch the Day Plan");

        autoschedule(trips, true);
        assertEquals(AutoScheduleStatus.CONFLICT, viewModel.getState().getStatus(),
                "a fresh attempt runs and reports the conflict that is still real");
    }

    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = text.indexOf(fragment);
        while (index >= 0) {
            count++;
            index = text.indexOf(fragment, index + fragment.length());
        }
        return count;
    }
}
