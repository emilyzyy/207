package interface_adapter.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import entity.entities.Trip;
import entity.valueobjects.TransportationMode;
import interface_adapter.viewmodels.DashboardState;
import interface_adapter.viewmodels.DashboardViewModel;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.TripOptionsState;
import interface_adapter.viewmodels.TripOptionsViewModel;

/**
 * Extending the day's hours in Trip Options must reach the Day Plan timeline straight away.
 *
 * <p>Reported from the running application: the popup said "Trip options saved" and the
 * timeline carried on drawing the old 9:00 AM – 5:00 PM window.</p>
 *
 * <p>The Day Plan learns the trip's hours from a listener on the Trip Options view model
 * ({@code AppBuilder} wiring), and redraws itself from a listener on its own. So the order
 * the Presenter publishes those two states in decides whether the redraw sees the new hours
 * or the old ones — publishing the schedule first repaints against hours that have not
 * arrived yet, and nothing repaints again afterwards. This test pins that order.</p>
 */
class TripOptionsHoursReachTheDayPlanTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    /** The panel's cached copy of the trip's hours, as {@code setTripDefaults} keeps them. */
    private static final class PanelHours {
        private LocalTime start = LocalTime.of(9, 0);
        private LocalTime end = LocalTime.of(17, 0);
        private LocalTime drawnStart;
        private LocalTime drawnEnd;
    }

    @Test
    void theTimelineRedrawsWithTheHoursTheTravellerJustSaved() {
        Trip trip = new Trip("t", "Toronto", DATE, LocalTime.of(9, 0), LocalTime.of(17, 0),
                TransportationMode.WALKING);
        DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("Toronto", DATE, "", ""));
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState("t",
                Collections.emptyList(), "", false, Collections.emptyList()));
        TripOptionsViewModel options = new TripOptionsViewModel(
                TripOptionsState.fromTrip(trip, "", false));

        PanelHours panel = new PanelHours();
        // Exactly the two AppBuilder listeners: one caches the hours, one redraws the timeline.
        options.addPropertyChangeListener(event -> {
            panel.start = options.getState().getStartTime();
            panel.end = options.getState().getEndTime();
        });
        dayPlan.addPropertyChangeListener(event -> {
            panel.drawnStart = panel.start;
            panel.drawnEnd = panel.end;
        });

        // The traveller extends the day to 8:00 AM - 9:00 PM and saves.
        trip.updateOptionsPreservingSchedule("Toronto", DATE,
                LocalTime.of(8, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        new TripOptionsPresenter(dashboard, dayPlan, options)
                .presentSuccess(trip, "Trip options saved.");

        assertEquals(LocalTime.of(8, 0), panel.drawnStart,
                "the timeline redrew against the old opening hour");
        assertEquals(LocalTime.of(21, 0), panel.drawnEnd,
                "the timeline redrew against the old closing hour");
    }

    /** And the hours the panel is left holding are the saved ones, whatever the order. */
    @Test
    void theSavedHoursAreTheOnesLeftInPlace() {
        Trip trip = new Trip("t", "Toronto", DATE, LocalTime.of(9, 0), LocalTime.of(17, 0),
                TransportationMode.WALKING);
        DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("Toronto", DATE, "", ""));
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState("t",
                Collections.emptyList(), "", false, Collections.emptyList()));
        TripOptionsViewModel options = new TripOptionsViewModel(
                TripOptionsState.fromTrip(trip, "", false));

        trip.updateOptionsPreservingSchedule("Toronto", DATE,
                LocalTime.of(8, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        new TripOptionsPresenter(dashboard, dayPlan, options)
                .presentSuccess(trip, "Trip options saved.");

        assertEquals(LocalTime.of(8, 0), options.getState().getStartTime());
        assertEquals(LocalTime.of(21, 0), options.getState().getEndTime());
    }
}
