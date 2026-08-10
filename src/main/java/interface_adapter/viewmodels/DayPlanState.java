package interface_adapter.viewmodels;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import entity.entities.ScheduledEvent;
import entity.entities.WeatherWarning;

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
    private final List<LocalDate> tripDates;
    private final int activeDayIndex;

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
    private final List<ImprovementView> improvements;
    private final List<ConstraintChipView> constraintChips;
    private final String tradeOff;

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

    /** Multi-day form: the hourly forecast plus which days exist and which is active. */
    public DayPlanState(
            String tripId, List<ScheduledEvent> events, String message, boolean error,
            List<WeatherWarning> hourlyWeather, List<LocalDate> tripDates, int activeDayIndex) {
        this(tripId, events, message, error, hourlyWeather, AutoScheduleStatus.IDLE,
                Collections.<PreviewRowView>emptyList(), null,
                Collections.<String>emptyList(), "", true, true, "", "",
                Collections.<String>emptySet(), Collections.<ImprovementView>emptyList(),
                tripDates, activeDayIndex);
    }

    public DayPlanState(String tripId, List<ScheduledEvent> events, String message, boolean error,
                        List<WeatherWarning> hourlyWeather,
                        AutoScheduleStatus status, List<PreviewRowView> previewRows,
                        PreviewMetricsView metrics, List<String> warnings,
                        String objectiveSummary, boolean keptCurrentOrder,
                        boolean searchCompletedWithinLimit, String travelQualityNote,
                        String previewFingerprint, Set<String> lockedEventIds) {
        this(tripId, events, message, error, hourlyWeather, status, previewRows, metrics,
                warnings, objectiveSummary, keptCurrentOrder, searchCompletedWithinLimit,
                travelQualityNote, previewFingerprint, lockedEventIds,
                Collections.<ImprovementView>emptyList());
    }

    public DayPlanState(String tripId, List<ScheduledEvent> events, String message, boolean error,
                        List<WeatherWarning> hourlyWeather,
                        AutoScheduleStatus status, List<PreviewRowView> previewRows,
                        PreviewMetricsView metrics, List<String> warnings,
                        String objectiveSummary, boolean keptCurrentOrder,
                        boolean searchCompletedWithinLimit, String travelQualityNote,
                        String previewFingerprint, Set<String> lockedEventIds,
                        List<ImprovementView> improvements) {
        this(tripId, events, message, error, hourlyWeather, status, previewRows, metrics,
                warnings, objectiveSummary, keptCurrentOrder, searchCompletedWithinLimit,
                travelQualityNote, previewFingerprint, lockedEventIds, improvements,
                Collections.<LocalDate>emptyList(), 0);
    }

    /**
     * Preview/conflict/failure form without improvements, plus which days exist.
     * @param message the m es sa ge value
     * @param error the e rr or value
     * @param tripId the t ri pi d value
     * @param events the e ve nt s value
     */
    public DayPlanState(String tripId, List<ScheduledEvent> events, String message, boolean error,
                        List<WeatherWarning> hourlyWeather,
                        AutoScheduleStatus status, List<PreviewRowView> previewRows,
                        PreviewMetricsView metrics, List<String> warnings,
                        String objectiveSummary, boolean keptCurrentOrder,
                        boolean searchCompletedWithinLimit, String travelQualityNote,
                        String previewFingerprint, Set<String> lockedEventIds,
                        List<LocalDate> tripDates, int activeDayIndex) {
        this(tripId, events, message, error, hourlyWeather, status, previewRows, metrics,
                warnings, objectiveSummary, keptCurrentOrder, searchCompletedWithinLimit,
                travelQualityNote, previewFingerprint, lockedEventIds,
                Collections.<ImprovementView>emptyList(), tripDates, activeDayIndex);
    }

    public DayPlanState(String tripId, List<ScheduledEvent> events, String message, boolean error,
                        List<WeatherWarning> hourlyWeather,
                        AutoScheduleStatus status, List<PreviewRowView> previewRows,
                        PreviewMetricsView metrics, List<String> warnings,
                        String objectiveSummary, boolean keptCurrentOrder,
                        boolean searchCompletedWithinLimit, String travelQualityNote,
                        String previewFingerprint, Set<String> lockedEventIds,
                        List<ImprovementView> improvements,
                        List<LocalDate> tripDates, int activeDayIndex) {
        this.tripId = tripId == null ? "" : tripId.trim();
        this.events = Collections.unmodifiableList(new ArrayList<ScheduledEvent>(
                events == null ? Collections.emptyList() : events));
        this.message = message == null ? "" : message;
        this.error = error;
        this.hourlyWeather = Collections.unmodifiableList(new ArrayList<WeatherWarning>(
                hourlyWeather == null ? Collections.<WeatherWarning>emptyList() : hourlyWeather));
        this.tripDates = Collections.unmodifiableList(new ArrayList<LocalDate>(
                tripDates == null ? Collections.<LocalDate>emptyList() : tripDates));
        this.activeDayIndex = Math.max(0, activeDayIndex);
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
        this.improvements = Collections.unmodifiableList(new ArrayList<>(
                improvements == null
                        ? Collections.<ImprovementView>emptyList() : improvements));
        // Chips and the trade-off are chosen after the figures are known, so they arrive by
        // withReasoning rather than through every constructor in this class.
        this.constraintChips = Collections.<ConstraintChipView>emptyList();
        this.tradeOff = "";
    }

    public String getTripId() {
        return tripId;
    }
    /**
     * Every day of the trip, in order; empty for callers that predate multi-day trips.
     * @return the result of the operation
     */

    public List<LocalDate> getTripDates() {
        return tripDates;
    }
    /**
     * Which day the Day Plan is showing; 0 for single-day trips and legacy callers.
     * @return the result of the operation
     */

    public int getActiveDayIndex() {
        return activeDayIndex;
    }
    /**
     * The itinerary as it really stands, never a proposal.
     * @return the result of the operation
     */

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
    /**
     * The proposed schedule while a Preview is on screen; empty otherwise.
     * @return the result of the operation
     */

    public List<PreviewRowView> getPreviewRows() {
        return previewRows;
    }
    /**
     * Before-and-after figures for the proposal, or null when there is none.
     * @return the result of the operation
     */

    public PreviewMetricsView getMetrics() {
        return metrics;
    }

    public List<String> getWarnings() {
        return warnings;
    }
    /**
     * One sentence naming what the schedule was arranged for.
     * @return the result of the operation
     */

    public String getObjectiveSummary() {
        return objectiveSummary;
    }

    public boolean isKeptCurrentOrder() {
        return keptCurrentOrder;
    }
    /**
     * False when the search stopped at its limit, so the UI avoids claiming the best.
     * @return the result of the operation
     */

    public boolean isSearchCompletedWithinLimit() {
        return searchCompletedWithinLimit;
    }
    /**
     * Wording about how much the travel times can be trusted; empty when unremarkable.
     * @return the result of the operation
     */

    public String getTravelQualityNote() {
        return travelQualityNote;
    }
    /**
     * Identifies the plan the Preview was built from, so a stale Apply is refused.
     * @return the result of the operation
     */

    public String getPreviewFingerprint() {
        return previewFingerprint;
    }
    /** Proven before/after gains, for the "Schedule improvements" stack; empty when none. */
    /**
     * Requirements the schedule worked around; smaller and quieter than an improvement.
     * @return the result of the operation
     */

    public List<ConstraintChipView> getConstraintChips() {
        return constraintChips;
    }
    /**
     * One sentence naming a disadvantage the schedule accepted, or empty.
     * @return the result of the operation
     */

    public String getTradeOff() {
        return tradeOff;
    }
    /**
     * The same state carrying the reasoning the Presenter selected for this proposal.
     * @param tradeOffSentence the t ra de of fs en te nc e value
     * @param chips the c hi ps value
     * @return the result of the operation
     */

    public DayPlanState withReasoning(List<ConstraintChipView> chips, String tradeOffSentence) {
        return new DayPlanState(this, chips, tradeOffSentence);
    }

    private DayPlanState(DayPlanState source, List<ConstraintChipView> chips,
                         String tradeOffSentence) {
        this.tripId = source.tripId;
        this.events = source.events;
        this.message = source.message;
        this.error = source.error;
        this.hourlyWeather = source.hourlyWeather;
        this.status = source.status;
        this.previewRows = source.previewRows;
        this.metrics = source.metrics;
        this.warnings = source.warnings;
        this.objectiveSummary = source.objectiveSummary;
        this.keptCurrentOrder = source.keptCurrentOrder;
        this.searchCompletedWithinLimit = source.searchCompletedWithinLimit;
        this.travelQualityNote = source.travelQualityNote;
        this.previewFingerprint = source.previewFingerprint;
        this.lockedEventIds = source.lockedEventIds;
        this.improvements = source.improvements;
        this.tripDates = source.tripDates;
        this.activeDayIndex = source.activeDayIndex;
        this.constraintChips = Collections.unmodifiableList(new ArrayList<>(
                chips == null ? Collections.<ConstraintChipView>emptyList() : chips));
        this.tradeOff = tradeOffSentence == null ? "" : tradeOffSentence;
    }

    public List<ImprovementView> getImprovements() {
        return improvements;
    }
    /**
     * Activities the traveller pinned, remembered for as long as the app is open.
     * @return the result of the operation
     */

    public Set<String> getLockedEventIds() {
        return lockedEventIds;
    }
    /**
     * Same itinerary and locks, with Autoschedule returned to its resting state.
     * @param newMessage the n ew me ss ag e value
     * @return the result of the operation
     */

    public DayPlanState clearedPreview(String newMessage) {
        return new DayPlanState(tripId, events, newMessage, false, hourlyWeather, AutoScheduleStatus.IDLE,
                Collections.<PreviewRowView>emptyList(), null, Collections.<String>emptyList(),
                "", keptCurrentOrder, true, "", "", lockedEventIds,
                // Improvements are claims about a proposal. Carrying them past the proposal
                // they describe leaves the screen holding cards for a schedule that no longer
                // exists, so they go with the rows and the figures.
                Collections.<ImprovementView>emptyList(), tripDates, activeDayIndex);
    }

    /**
     * The same state with a fresh hourly forecast and nothing else touched.
     *
     * <p>The forecast arrives on a background worker whenever it happens to arrive. Rebuilding
     * the state from the short constructor instead reset the status to IDLE and emptied the
     * rows, so a forecast landing while a Preview was open silently threw the proposal away,
     * along with the traveller's pins.</p>
      * @param updatedWeather the u pd at ed we at he r value
      * @return the result of the operation
     */
    public DayPlanState withHourlyWeather(List<WeatherWarning> updatedWeather) {
        return new DayPlanState(tripId, events, message, error, updatedWeather, status,
                previewRows, metrics, warnings, objectiveSummary, keptCurrentOrder,
                searchCompletedWithinLimit, travelQualityNote, previewFingerprint,
                lockedEventIds, improvements, tripDates, activeDayIndex);
    }

    /**
     * The same day with a blocking notice dismissed.
     *
     * <p>Dismissing is only about the message. The saved day, the pins and the forecast are
     * untouched, and Autoschedule stays available — the whole point of the OK button is that
     * the traveller can now change something and try again.</p>
      * @return the result of the operation
     */
    public DayPlanState withoutNotice() {
        return new DayPlanState(tripId, events, "", false, hourlyWeather,
                AutoScheduleStatus.IDLE, Collections.<PreviewRowView>emptyList(), null,
                Collections.<String>emptyList(), "", keptCurrentOrder, true, "", "",
                lockedEventIds, Collections.<ImprovementView>emptyList(),
                tripDates, activeDayIndex);
    }

    /**
     * Whether a blocking notice is on screen: a conflict or a failure, never a Preview.
     * @return the result of the operation
     */
    public boolean hasBlockingNotice() {
        return error && !message.isEmpty()
                && (status == AutoScheduleStatus.CONFLICT || status == AutoScheduleStatus.FAILURE);
    }

    /**
     * Same state with a different set of pinned activities.
     * @param updatedLockIds the u pd at ed lo ck id s value
     * @return the result of the operation
     */
    public DayPlanState withLocks(Set<String> updatedLockIds) {
        return new DayPlanState(tripId, events, message, error, hourlyWeather, status, previewRows, metrics,
                warnings, objectiveSummary, keptCurrentOrder, searchCompletedWithinLimit,
                travelQualityNote, previewFingerprint, updatedLockIds, improvements,
                tripDates, activeDayIndex);
    }

    /**
     * Same state showing that work is under way.
     * @param loadingMessage the l oa di ng me ss ag e value
     * @return the result of the operation
     */
    public DayPlanState loading(String loadingMessage) {
        return new DayPlanState(tripId, events, loadingMessage, false, hourlyWeather, AutoScheduleStatus.LOADING,
                Collections.<PreviewRowView>emptyList(), null, Collections.<String>emptyList(),
                "", keptCurrentOrder, true, "", "", lockedEventIds,
                improvements, tripDates, activeDayIndex);
    }

    public List<WeatherWarning> getHourlyWeather() {
        return hourlyWeather;
    }
    /**
     * Selects every forecast hour that overlaps the event's half-open time interval.
     * @param event the e ve nt value
     * @return the result of the operation
     */

    public List<WeatherWarning> getHourlyWeatherFor(ScheduledEvent event) {
        if (event == null) {
            return Collections.emptyList();
        }
        final List<WeatherWarning> result = new ArrayList<WeatherWarning>();
        for (WeatherWarning warning : hourlyWeather) {
            if (warning == null || warning.getTime() == null) {
                continue;
            }
            final java.time.LocalTime nextHour = warning.getTime().plusHours(1);
            final boolean reachesAfterStart = nextHour.isAfter(event.getStartTime())
                    || nextHour.isBefore(warning.getTime());
            if (warning.getTime().isBefore(event.getEndTime()) && reachesAfterStart) {
                result.add(warning);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
