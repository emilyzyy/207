package closeai.adapters.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Coordinates calendar navigation while observing the same trip and schedule state as the
 * dashboard. Calendar navigation is a presentation concern and does not mutate the trip.
 */
public final class CalendarViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private final DashboardViewModel dashboard;
    private final DayPlanViewModel dayPlan;
    private final Supplier<LocalDate> today;
    private CalendarState state;

    public CalendarViewModel(
            DashboardViewModel dashboard,
            DayPlanViewModel dayPlan) {
        this(dashboard, dayPlan, LocalDate::now);
    }

    public CalendarViewModel(
            DashboardViewModel dashboard,
            DayPlanViewModel dayPlan,
            Supplier<LocalDate> today) {
        if (dashboard == null || dayPlan == null || today == null) {
            throw new IllegalArgumentException("Calendar dependencies are required");
        }
        this.dashboard = dashboard;
        this.dayPlan = dayPlan;
        this.today = today;
        LocalDate tripDate = dashboard.getState().getDate();
        LocalDate initialFocus = tripDate == null
                ? Objects.requireNonNull(today.get(), "Today is required") : tripDate;
        state = new CalendarState(
                CalendarViewMode.MONTH,
                initialFocus,
                tripDate,
                dashboard.getState().getDestination(),
                dayPlan.getState().getEvents());
        dashboard.addPropertyChangeListener(event -> synchronizeTrip());
        dayPlan.addPropertyChangeListener(event -> synchronizeSchedule());
    }

    public CalendarState getState() {
        return state;
    }

    public void setViewMode(CalendarViewMode viewMode) {
        update(new CalendarState(
                Objects.requireNonNull(viewMode, "Calendar view is required"),
                state.getFocusDate(), state.getTripDate(),
                state.getDestination(), state.getEvents()));
    }

    public void selectDate(LocalDate date) {
        update(new CalendarState(
                state.getViewMode(), Objects.requireNonNull(date, "Date is required"),
                state.getTripDate(), state.getDestination(), state.getEvents()));
    }

    public void previousPeriod() {
        selectDate(shift(state.getFocusDate(), -1));
    }

    public void nextPeriod() {
        selectDate(shift(state.getFocusDate(), 1));
    }

    public void goToToday() {
        selectDate(Objects.requireNonNull(today.get(), "Today is required"));
    }

    public void goToTripDate() {
        if (state.getTripDate() != null) {
            selectDate(state.getTripDate());
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }

    private LocalDate shift(LocalDate date, int amount) {
        switch (state.getViewMode()) {
            case DAY:
                return date.plusDays(amount);
            case WEEK:
                return date.plusWeeks(amount);
            case MONTH:
                return date.plusMonths(amount);
            default:
                throw new IllegalStateException("Unsupported calendar view");
        }
    }

    private void synchronizeTrip() {
        DashboardState dashboardState = dashboard.getState();
        LocalDate nextTripDate = dashboardState.getDate();
        LocalDate focus = state.getFocusDate();
        if (!Objects.equals(state.getTripDate(), nextTripDate) && nextTripDate != null) {
            focus = nextTripDate;
        }
        update(new CalendarState(
                state.getViewMode(), focus, nextTripDate,
                dashboardState.getDestination(), dayPlan.getState().getEvents()));
    }

    private void synchronizeSchedule() {
        update(new CalendarState(
                state.getViewMode(), state.getFocusDate(), state.getTripDate(),
                state.getDestination(), dayPlan.getState().getEvents()));
    }

    private void update(CalendarState updatedState) {
        CalendarState oldState = state;
        state = updatedState;
        changes.firePropertyChange("state", oldState, state);
    }
}
