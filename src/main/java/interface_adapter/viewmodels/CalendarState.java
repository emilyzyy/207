package interface_adapter.viewmodels;

import entity.entities.ScheduledEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable calendar display state derived from the active trip and day plan. */
public final class CalendarState {
    private final CalendarViewMode viewMode;
    private final LocalDate focusDate;
    private final List<LocalDate> tripDates;
    private final int activeDayIndex;
    private final String destination;
    private final List<ScheduledEvent> events;

    public CalendarState(
            CalendarViewMode viewMode,
            LocalDate focusDate,
            LocalDate tripDate,
            String destination,
            List<ScheduledEvent> events) {
        this(viewMode, focusDate,
                tripDate == null ? Collections.<LocalDate>emptyList()
                        : Collections.singletonList(tripDate),
                0, destination, events);
    }

    /** Multi-day form: every trip date plus which day is currently active. */
    public CalendarState(
            CalendarViewMode viewMode,
            LocalDate focusDate,
            List<LocalDate> tripDates,
            int activeDayIndex,
            String destination,
            List<ScheduledEvent> events) {
        if (viewMode == null || focusDate == null) {
            throw new IllegalArgumentException("Calendar view and focus date are required");
        }
        this.viewMode = viewMode;
        this.focusDate = focusDate;
        this.tripDates = Collections.unmodifiableList(new ArrayList<LocalDate>(
                tripDates == null ? Collections.<LocalDate>emptyList() : tripDates));
        this.activeDayIndex = clamp(this.tripDates, activeDayIndex);
        this.destination = destination == null ? "" : destination;
        this.events = Collections.unmodifiableList(new ArrayList<ScheduledEvent>(
                events == null ? Collections.emptyList() : events));
    }

    public CalendarViewMode getViewMode() {
        return viewMode;
    }

    public LocalDate getFocusDate() {
        return focusDate;
    }

    public LocalDate getTripDate() {
        return tripDates.isEmpty() ? null : tripDates.get(activeDayIndex);
    }

    public List<LocalDate> getTripDates() {
        return tripDates;
    }

    public int getActiveDayIndex() {
        return activeDayIndex;
    }

    public String getDestination() {
        return destination;
    }

    public List<ScheduledEvent> getEvents() {
        return events;
    }

    public boolean isTripDate(LocalDate date) {
        return date != null && tripDates.contains(date);
    }

    public boolean isActiveTripDate(LocalDate date) {
        return date != null && date.equals(getTripDate());
    }

    private static int clamp(List<LocalDate> dates, int index) {
        if (dates.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(index, dates.size() - 1));
    }
}
