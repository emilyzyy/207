package interface_adapter.viewmodels;

import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CalendarViewModelTest {

    @Test
    void navigatesBySelectedScaleAndReturnsToTodayOrTripDate() {
        LocalDate today = LocalDate.of(2026, 8, 3);
        LocalDate tripDate = LocalDate.of(2026, 8, 12);
        DashboardViewModel dashboard = dashboard("Toronto", tripDate);
        DayPlanViewModel dayPlan = dayPlan(Collections.emptyList());
        CalendarViewModel calendar = new CalendarViewModel(
                dashboard, dayPlan, () -> today);

        assertEquals(CalendarViewMode.MONTH, calendar.getState().getViewMode());
        assertEquals(tripDate, calendar.getState().getFocusDate());

        calendar.nextPeriod();
        assertEquals(LocalDate.of(2026, 9, 12), calendar.getState().getFocusDate());
        calendar.setViewMode(CalendarViewMode.WEEK);
        calendar.previousPeriod();
        assertEquals(LocalDate.of(2026, 9, 5), calendar.getState().getFocusDate());
        calendar.setViewMode(CalendarViewMode.DAY);
        calendar.nextPeriod();
        assertEquals(LocalDate.of(2026, 9, 6), calendar.getState().getFocusDate());

        calendar.goToToday();
        assertEquals(today, calendar.getState().getFocusDate());
        calendar.goToTripDate();
        assertEquals(tripDate, calendar.getState().getFocusDate());
    }

    @Test
    void observesTripEditsAndScheduleChangesWithoutDuplicatingState() {
        LocalDate today = LocalDate.of(2026, 8, 3);
        DashboardViewModel dashboard = dashboard("", null);
        DayPlanViewModel dayPlan = dayPlan(Collections.emptyList());
        CalendarViewModel calendar = new CalendarViewModel(
                dashboard, dayPlan, () -> today);
        assertNull(calendar.getState().getTripDate());

        LocalDate editedDate = LocalDate.of(2026, 10, 4);
        dashboard.setState(new DashboardState("Montreal", editedDate, "Clear", ""));
        assertEquals(editedDate, calendar.getState().getTripDate());
        assertEquals(editedDate, calendar.getState().getFocusDate());
        assertEquals("Montreal", calendar.getState().getDestination());

        ScheduledEvent travel = new ScheduledEvent(
                "travel-1", null, LocalTime.of(9, 0), LocalTime.of(9, 20),
                EventType.TRAVEL, "Walk downtown");
        dayPlan.setState(new DayPlanState(
                "trip-1", Collections.singletonList(travel), "Updated", false));
        assertEquals(1, calendar.getState().getEvents().size());
        assertEquals("travel-1", calendar.getState().getEvents().get(0).getId());
    }

    private DashboardViewModel dashboard(String destination, LocalDate date) {
        return new DashboardViewModel(
                new DashboardState(destination, date, "", ""));
    }

    private DayPlanViewModel dayPlan(java.util.List<ScheduledEvent> events) {
        return new DayPlanViewModel(new DayPlanState("trip-1", events, "", false));
    }
}
