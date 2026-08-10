package interface_adapter.viewmodels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Day Plan state is shared: the Calendar observes it too. Autoschedule added fields
 * to it, so these tests pin down that everything which existed before still behaves
 * exactly as it did, and that a proposal cannot leak into the Calendar.
 */
class DayPlanStateCompatibilityTest {

    private static ScheduledEvent event(String id, int hour) {
        Activity activity = new Activity(id, "Name of " + id, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(9, 0), LocalTime.of(21, 0), IndoorOutdoorType.INDOOR, "none");
        return new ScheduledEvent(id, activity, LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0), EventType.ACTIVITY, "");
    }

    @Test
    void theOriginalFourArgumentConstructorStillWorksUnchanged() {
        List<ScheduledEvent> events = Arrays.asList(event("a", 9), event("b", 14));

        DayPlanState state = new DayPlanState("trip-1", events, "hello", true);

        assertEquals("trip-1", state.getTripId());
        assertEquals(2, state.getEvents().size());
        assertEquals("hello", state.getMessage());
        assertTrue(state.isError());
    }

    @Test
    void aStateBuiltTheOldWayIsSimplyIdle() {
        DayPlanState state = new DayPlanState("trip-1", Arrays.asList(event("a", 9)), "", false);

        assertEquals(AutoScheduleStatus.IDLE, state.getStatus());
        assertTrue(state.getPreviewRows().isEmpty());
        assertTrue(state.getWarnings().isEmpty());
        assertTrue(state.getLockedEventIds().isEmpty());
    }

    @Test
    void nullsAreStillNormalisedTheSameWay() {
        DayPlanState state = new DayPlanState(null, null, null, false);

        assertEquals("", state.getTripId());
        assertTrue(state.getEvents().isEmpty());
        assertEquals("", state.getMessage());
    }

    @Test
    void eventsRemainUnmodifiableForObservers() {
        DayPlanState state = new DayPlanState("trip-1", Arrays.asList(event("a", 9)), "", false);

        try {
            state.getEvents().add(event("b", 12));
            org.junit.jupiter.api.Assertions.fail("events should stay unmodifiable");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
    }

    @Test
    void theCalendarSeesTheSameScheduleThroughItsOwnViewModel() {
        DayPlanViewModel dayPlan = new DayPlanViewModel(
                new DayPlanState("trip-1", Arrays.asList(event("a", 9)), "", false));
        DashboardViewModel dashboard = new DashboardViewModel(new DashboardState(
                "Toronto", LocalDate.of(2026, 8, 12), "", ""));
        CalendarViewModel calendar = new CalendarViewModel(dashboard, dayPlan,
                () -> LocalDate.of(2026, 8, 12));

        assertEquals(1, calendar.getState().getEvents().size());

        dayPlan.setState(new DayPlanState("trip-1",
                Arrays.asList(event("a", 10), event("b", 13)), "", false));

        assertEquals(2, calendar.getState().getEvents().size(),
                "the Calendar must still follow schedule changes");
        assertEquals(LocalTime.of(10, 0), calendar.getState().getEvents().get(0).getStartTime());
    }

    @Test
    void aPreviewIsNotVisibleToTheCalendar() {
        DayPlanViewModel dayPlan = new DayPlanViewModel(
                new DayPlanState("trip-1", Arrays.asList(event("a", 9)), "", false));
        DashboardViewModel dashboard = new DashboardViewModel(new DashboardState(
                "Toronto", LocalDate.of(2026, 8, 12), "", ""));
        CalendarViewModel calendar = new CalendarViewModel(dashboard, dayPlan,
                () -> LocalDate.of(2026, 8, 12));

        List<PreviewRowView> proposal = new ArrayList<>();
        proposal.add(new PreviewRowView("a", "Name of a", PreviewRowView.Kind.ACTIVITY,
                LocalTime.of(15, 0), LocalTime.of(16, 0), false, true, "", null));
        dayPlan.setState(new DayPlanState("trip-1", Arrays.asList(event("a", 9)), "", false, java.util.Collections.emptyList(), AutoScheduleStatus.PREVIEW, proposal, null, java.util.Collections.emptyList(),
                "", true, true, "", "fingerprint", java.util.Collections.emptySet()));

        assertEquals(LocalTime.of(9, 0), calendar.getState().getEvents().get(0).getStartTime(),
                "the Calendar must show the agreed time, not the proposed one");
    }

    @Test
    void locksAreRememberedWithoutDisturbingAnythingElse() {
        DayPlanState state = new DayPlanState("trip-1", Arrays.asList(event("a", 9)), "", false);

        DayPlanState locked = state.withLocks(
                new java.util.LinkedHashSet<>(Arrays.asList("a")));

        assertTrue(locked.getLockedEventIds().contains("a"));
        assertEquals(state.getEvents(), locked.getEvents());
        assertEquals(state.getTripId(), locked.getTripId());
    }

    @Test
    void clearingAPreviewKeepsTheItineraryAndTheLocks() {
        DayPlanState previewing = new DayPlanState("trip-1", Arrays.asList(event("a", 9)),
                "proposed", false, java.util.Collections.emptyList(), AutoScheduleStatus.PREVIEW,
                Arrays.asList(new PreviewRowView("a", "a", PreviewRowView.Kind.ACTIVITY,
                        LocalTime.of(15, 0), LocalTime.of(16, 0), false, true, "", null)),
                null, java.util.Collections.emptyList(), "", true, true, "", "fp",
                new java.util.LinkedHashSet<>(Arrays.asList("a")));

        DayPlanState cancelled = previewing.clearedPreview("Autoschedule cancelled.");

        assertEquals(AutoScheduleStatus.IDLE, cancelled.getStatus());
        assertTrue(cancelled.getPreviewRows().isEmpty());
        assertFalse(cancelled.isError());
        assertEquals(1, cancelled.getEvents().size());
        assertTrue(cancelled.getLockedEventIds().contains("a"));
    }
}
