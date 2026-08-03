package closeai.adapters.viewmodels;

import closeai.domain.entities.ScheduledEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable calendar display state derived from the active trip and day plan. */
public final class CalendarState {
    private final CalendarViewMode viewMode;
    private final LocalDate focusDate;
    private final LocalDate tripDate;
    private final String destination;
    private final List<ScheduledEvent> events;

    public CalendarState(
            CalendarViewMode viewMode,
            LocalDate focusDate,
            LocalDate tripDate,
            String destination,
            List<ScheduledEvent> events) {
        if (viewMode == null || focusDate == null) {
            throw new IllegalArgumentException("Calendar view and focus date are required");
        }
        this.viewMode = viewMode;
        this.focusDate = focusDate;
        this.tripDate = tripDate;
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
        return tripDate;
    }

    public String getDestination() {
        return destination;
    }

    public List<ScheduledEvent> getEvents() {
        return events;
    }

    public boolean isTripDate(LocalDate date) {
        return tripDate != null && tripDate.equals(date);
    }
}
