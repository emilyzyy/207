package interface_adapter.viewmodels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.WeatherWarning;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.WeatherSeverity;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What survives, and what must not, as a Preview moves through its life.
 *
 * <p>A Preview is a proposal that has not been saved. Everything on screen while it is open —
 * the rows, the figures, the improvement cards — describes that one proposal, so all of it
 * has to appear and disappear together. Two paths were getting this wrong in opposite
 * directions: cancelling kept the improvement cards for a proposal that no longer existed,
 * and a background forecast arriving mid-Preview threw the whole proposal away.</p>
 */
class PreviewStateLifecycleTest {

    private static ScheduledEvent event(String id) {
        Activity activity = new Activity(id, "Place " + id, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(8, 0), LocalTime.of(20, 0), IndoorOutdoorType.INDOOR, "none");
        return new ScheduledEvent(id, activity, LocalTime.of(9, 0), LocalTime.of(10, 0),
                EventType.ACTIVITY, "");
    }

    private static DayPlanState openPreview() {
        List<PreviewRowView> rows = Collections.singletonList(
                new PreviewRowView("a", "Place a", PreviewRowView.Kind.ACTIVITY,
                        LocalTime.of(11, 0), LocalTime.of(12, 0), false, true,
                        "a usual mealtime", Collections.singletonList("a usual mealtime")));
        return new DayPlanState("trip-1", Collections.singletonList(event("a")),
                "Proposed schedule", false, Collections.emptyList(),
                AutoScheduleStatus.PREVIEW, rows,
                new PreviewMetricsView(30, 20, 90, 10, 1, 1, 120),
                Collections.singletonList("Travel times may include estimates."),
                "Arranged for less travel", true, true, "", "fingerprint",
                Collections.singleton("a"),
                Collections.singletonList(
                        new ImprovementView("⏳", "80 min of waiting removed",
                                "Less dead time between activities")));
    }

    /**
     * Cancelling is a promise that nothing happened. Anything still on screen afterwards is
     * describing a schedule the traveller declined.
     */
    @Test
    void cancellingAPreviewLeavesNothingBehindThatDescribesIt() {
        DayPlanState cancelled = openPreview().clearedPreview("Autoschedule cancelled.");

        assertEquals(AutoScheduleStatus.IDLE, cancelled.getStatus());
        assertTrue(cancelled.getPreviewRows().isEmpty(), "the proposed rows must go");
        assertTrue(cancelled.getWarnings().isEmpty(), "its warnings must go");
        assertTrue(cancelled.getImprovements().isEmpty(),
                "its improvement cards must go too: " + cancelled.getImprovements());
        assertEquals(null, cancelled.getMetrics(), "its figures must go");
    }

    /** Cancelling changes nothing about the day itself. */
    @Test
    void cancellingAPreviewLeavesTheSavedDayExactlyAsItWas() {
        DayPlanState open = openPreview();
        DayPlanState cancelled = open.clearedPreview("Autoschedule cancelled.");

        assertEquals(open.getEvents(), cancelled.getEvents(),
                "the saved day is not the scheduler's to change on cancel");
        assertEquals(open.getLockedEventIds(), cancelled.getLockedEventIds(),
                "pins are the traveller's, and outlive a proposal they declined");
    }

    /**
     * The forecast arrives when it arrives. A Preview open at that moment must still be open
     * afterwards, with the same proposal in it.
     */
    @Test
    void aForecastArrivingDuringAPreviewDoesNotDiscardTheProposal() {
        DayPlanState open = openPreview();
        List<WeatherWarning> forecast = Arrays.asList(
                new WeatherWarning(new Location(43.65, -79.38, "Toronto"), LocalTime.of(11, 0),
                        "Sunny", WeatherSeverity.LOW, "22°C"),
                new WeatherWarning(new Location(43.65, -79.38, "Toronto"), LocalTime.of(12, 0),
                        "Showers", WeatherSeverity.MEDIUM, "19°C"));

        DayPlanState updated = open.withHourlyWeather(forecast);

        assertEquals(AutoScheduleStatus.PREVIEW, updated.getStatus(),
                "a forecast is not a reason to close a Preview");
        assertEquals(open.getPreviewRows(), updated.getPreviewRows());
        assertEquals(open.getImprovements(), updated.getImprovements());
        assertEquals(open.getLockedEventIds(), updated.getLockedEventIds(),
                "and it is certainly not a reason to unpin things");
        assertEquals(open.getPreviewFingerprint(), updated.getPreviewFingerprint(),
                "Apply must still recognise the day the proposal was made against");
        assertEquals(forecast, updated.getHourlyWeather(), "the new forecast should be there");
        assertNotEquals(open.getHourlyWeather(), updated.getHourlyWeather());
    }
}
