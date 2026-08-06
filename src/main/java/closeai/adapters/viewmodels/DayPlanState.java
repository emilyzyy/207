package closeai.adapters.viewmodels;

import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.WeatherWarning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable display state shared by the Day Plan and Calendar views.
 *
 * <p>The original four values — trip, events, message and error flag — keep their exact
 * meaning and are what the Calendar reads, so nothing here changes for existing
 * observers. Everything Autoschedule needs is added alongside them.</p>
 *
 * <p>One rule matters above the rest: {@link #getEvents()} is always the itinerary as it
 * really stands. A proposal lives in {@link #getPreviewRows()} and only becomes part of
 * the events once the traveller applies it, which is what stops the Calendar from
 * showing times nobody has agreed to.</p>
 */
public final class DayPlanState {
    private final String tripId;
    private final List<ScheduledEvent> events;
    private final String message;
    private final boolean error;
    private final List<WeatherWarning> hourlyWeather;

    private final AutoScheduleStatus status;
    private final List<PreviewRowView> previewRows;
    private final PreviewMetricsView metrics;
    private final List<String> warnings;
    private final String objectiveSummary;
    private final boolean keptCurrentOrder;
    private final boolean searchCompletedWithinLimit;
    private final String travelQualityNote;
    private final String previewFingerprint;
    private final Set<String> lockedEventIds;

    public DayPlanState(
            String tripId, List<ScheduledEvent> events, String message, boolean error) {
        this(tripId, events, message, error, Collections.<WeatherWarning>emptyList());
    }

    /** Shiyuan's hourly-forecast form, used by the dashboard and trip-setup presenters. */
    public DayPlanState(
            String tripId, List<ScheduledEvent> events, String message, boolean error,
            List<WeatherWarning> hourlyWeather) {
        this(tripId, events, message, error, hourlyWeather, AutoScheduleStatus.IDLE,
                Collections.<PreviewRowView>emptyList(), null,
                Collections.<String>emptyList(), "", true, true, "", "",
                Collections.<String>emptySet());
    }

    public DayPlanState(String tripId, List<ScheduledEvent> events, String message, boolean error,
                        List<WeatherWarning> hourlyWeather,
                        AutoScheduleStatus status, List<PreviewRowView> previewRows,
                        PreviewMetricsView metrics, List<String> warnings,
                        String objectiveSummary, boolean keptCurrentOrder,
                        boolean searchCompletedWithinLimit, String travelQualityNote,
                        String previewFingerprint, Set<String> lockedEventIds) {
        this.tripId = tripId == null ? "" : tripId.trim();
        this.events = Collections.unmodifiableList(new ArrayList<ScheduledEvent>(
                events == null ? Collections.emptyList() : events));
        this.message = message == null ? "" : message;
        this.error = error;
        this.hourlyWeather = Collections.unmodifiableList(new ArrayList<WeatherWarning>(
                hourlyWeather == null ? Collections.<WeatherWarning>emptyList() : hourlyWeather));
        this.status = status == null ? AutoScheduleStatus.IDLE : status;
        this.previewRows = Collections.unmodifiableList(new ArrayList<>(
                previewRows == null ? Collections.<PreviewRowView>emptyList() : previewRows));
        this.metrics = metrics;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(
                warnings == null ? Collections.<String>emptyList() : warnings));
        this.objectiveSummary = objectiveSummary == null ? "" : objectiveSummary;
        this.keptCurrentOrder = keptCurrentOrder;
        this.searchCompletedWithinLimit = searchCompletedWithinLimit;
        this.travelQualityNote = travelQualityNote == null ? "" : travelQualityNote;
        this.previewFingerprint = previewFingerprint == null ? "" : previewFingerprint;
        this.lockedEventIds = Collections.unmodifiableSet(new LinkedHashSet<>(
                lockedEventIds == null ? Collections.<String>emptySet() : lockedEventIds));
    }

    public String getTripId() {
        return tripId;
    }

    /** The itinerary as it really stands, never a proposal. */
    public List<ScheduledEvent> getEvents() {
        return events;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return error;
    }

    public AutoScheduleStatus getStatus() {
        return status;
    }

    /** The proposed schedule while a Preview is on screen; empty otherwise. */
    public List<PreviewRowView> getPreviewRows() {
        return previewRows;
    }

    /** Before-and-after figures for the proposal, or null when there is none. */
    public PreviewMetricsView getMetrics() {
        return metrics;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** One sentence naming what the schedule was arranged for. */
    public String getObjectiveSummary() {
        return objectiveSummary;
    }

    public boolean isKeptCurrentOrder() {
        return keptCurrentOrder;
    }

    /** False when the search stopped at its limit, so the UI avoids claiming the best. */
    public boolean isSearchCompletedWithinLimit() {
        return searchCompletedWithinLimit;
    }

    /** Wording about how much the travel times can be trusted; empty when unremarkable. */
    public String getTravelQualityNote() {
        return travelQualityNote;
    }

    /** Identifies the plan the Preview was built from, so a stale Apply is refused. */
    public String getPreviewFingerprint() {
        return previewFingerprint;
    }

    /** Activities the traveller pinned, remembered for as long as the app is open. */
    public Set<String> getLockedEventIds() {
        return lockedEventIds;
    }

    /** Same itinerary and locks, with Autoschedule returned to its resting state. */
    public DayPlanState clearedPreview(String newMessage) {
        return new DayPlanState(tripId, events, newMessage, false, hourlyWeather, AutoScheduleStatus.IDLE,
                Collections.<PreviewRowView>emptyList(), null, Collections.<String>emptyList(),
                "", keptCurrentOrder, true, "", "", lockedEventIds);
    }

    /** Same state with a different set of pinned activities. */
    public DayPlanState withLocks(Set<String> updatedLockIds) {
        return new DayPlanState(tripId, events, message, error, hourlyWeather, status, previewRows, metrics,
                warnings, objectiveSummary, keptCurrentOrder, searchCompletedWithinLimit,
                travelQualityNote, previewFingerprint, updatedLockIds);
    }

    /** Same state showing that work is under way. */
    public DayPlanState loading(String loadingMessage) {
        return new DayPlanState(tripId, events, loadingMessage, false, hourlyWeather, AutoScheduleStatus.LOADING,
                Collections.<PreviewRowView>emptyList(), null, Collections.<String>emptyList(),
                "", keptCurrentOrder, true, "", "", lockedEventIds);
    }

    public List<WeatherWarning> getHourlyWeather() {
        return hourlyWeather;
    }

    /** Selects every forecast hour that overlaps the event's half-open time interval. */
    public List<WeatherWarning> getHourlyWeatherFor(ScheduledEvent event) {
        if (event == null) return Collections.emptyList();
        List<WeatherWarning> result = new ArrayList<WeatherWarning>();
        for (WeatherWarning warning : hourlyWeather) {
            if (warning == null || warning.getTime() == null) continue;
            java.time.LocalTime nextHour = warning.getTime().plusHours(1);
            boolean reachesAfterStart = nextHour.isAfter(event.getStartTime())
                    || nextHour.isBefore(warning.getTime());
            if (warning.getTime().isBefore(event.getEndTime()) && reachesAfterStart) {
                result.add(warning);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
