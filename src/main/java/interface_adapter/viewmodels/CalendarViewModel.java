package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
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
        final LocalDate tripDate = dashboard.getState().getDate();
        final LocalDate initialFocus = tripDate == null
                ? Objects.requireNonNull(today.get(), "Today is required") : tripDate;
        state = new CalendarState(
                CalendarViewMode.MONTH,
                initialFocus,
                tripDates(),
                activeDayIndex(),
                dashboard.getState().getDestination(),
                dayPlan.getState().getEvents());
        dashboard.addPropertyChangeListener(event -> synchronizeTrip());
        dayPlan.addPropertyChangeListener(event -> synchronizeSchedule());
    }

    public CalendarState getState() {
        return state;
    }

    /**
     * Performs the s et vi ew mo de operation.
     * @param viewMode the v ie wm od e value
     */
    public void setViewMode(CalendarViewMode viewMode) {
        update(new CalendarState(
                Objects.requireNonNull(viewMode, "Calendar view is required"),
                state.getFocusDate(), state.getTripDates(), state.getActiveDayIndex(),
                state.getDestination(), state.getEvents()));
    }

    /**
     * Performs the s el ec td at e operation.
     * @param date the d at e value
     */
    public void selectDate(LocalDate date) {
        update(new CalendarState(
                state.getViewMode(), Objects.requireNonNull(date, "Date is required"),
                state.getTripDates(), state.getActiveDayIndex(),
                state.getDestination(), state.getEvents()));
    }

    /** Performs the p re vi ou sp er io d operation. */
    public void previousPeriod() {
        selectDate(shift(state.getFocusDate(), -1));
    }

    /** Performs the n ex tp er io d operation. */
    public void nextPeriod() {
        selectDate(shift(state.getFocusDate(), 1));
    }

    /** Performs the g ot ot od ay operation. */
    public void goToToday() {
        selectDate(Objects.requireNonNull(today.get(), "Today is required"));
    }

    /** Performs the g ot ot ri pd at e operation. */
    public void goToTripDate() {
        if (state.getTripDate() != null) {
            selectDate(state.getTripDate());
        }
    }

    /**
     * Registers a listener notified whenever this view model's state is replaced.
     *
     * @param listener the listener to notify
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    /**
     * Stops notifying the given listener of state changes.
     *
     * @param listener the listener to stop notifying
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }

    private List<LocalDate> tripDates() {
        final List<LocalDate> dayPlanDates = dayPlan.getState().getTripDates();
        if (!dayPlanDates.isEmpty()) {
            return dayPlanDates;
        }
        final LocalDate dashboardDate = dashboard.getState().getDate();
        return dashboardDate == null
                ? Collections.emptyList()
                : Collections.singletonList(dashboardDate);
    }

    private int activeDayIndex() {
        if (!dayPlan.getState().getTripDates().isEmpty()) {
            return dayPlan.getState().getActiveDayIndex();
        }
        return 0;
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
        final DashboardState dashboardState = dashboard.getState();
        final LocalDate nextTripDate = dashboardState.getDate();
        final List<LocalDate> nextTripDates = tripDates();
        final LocalDate nextActiveDate = nextTripDates.isEmpty() ? null : nextTripDates.get(0);
        LocalDate focus = state.getFocusDate();
        if (!Objects.equals(state.getTripDate(), nextActiveDate) && nextActiveDate != null) {
            focus = nextActiveDate;
        }
        update(new CalendarState(
                state.getViewMode(), focus, nextTripDates, activeDayIndex(),
                dashboardState.getDestination(), dayPlan.getState().getEvents()));
    }

    private void synchronizeSchedule() {
        // A Day Plan update can change the trip's dates and active day as well as its
        // events. Re-read the complete shared trip state so a Trip Options date edit
        // cannot leave the calendar showing the previous date.
        synchronizeTrip();
    }

    private void update(CalendarState updatedState) {
        final CalendarState oldState = state;
        state = updatedState;
        changes.firePropertyChange("state", oldState, state);
    }
}
