package closeai.adapters.viewmodels;

import closeai.domain.entities.ScheduledEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable display state shared by the Day Plan and Calendar views. */
public final class DayPlanState {
    private final String tripId;
    private final List<ScheduledEvent> events;
    private final String message;
    private final boolean error;

    public DayPlanState(
            String tripId, List<ScheduledEvent> events, String message, boolean error) {
        if (tripId == null || tripId.trim().isEmpty()) {
            throw new IllegalArgumentException("Trip id is required");
        }
        this.tripId = tripId;
        this.events = Collections.unmodifiableList(new ArrayList<ScheduledEvent>(
                events == null ? Collections.emptyList() : events));
        this.message = message == null ? "" : message;
        this.error = error;
    }

    public String getTripId() {
        return tripId;
    }

    public List<ScheduledEvent> getEvents() {
        return events;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return error;
    }
}
