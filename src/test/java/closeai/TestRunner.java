package closeai;

import closeai.application.AppContainer;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;

public final class TestRunner {
    public static void main(String[] args) {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = app.createTrip.execute("Toronto", LocalDate.of(2026, 7, 18),
                LocalTime.of(9, 0), LocalTime.of(19, 0), TransportationMode.TRANSIT);
        app.bookmarkActivity.execute(trip.getId(), "rom");
        app.bookmarkActivity.execute(trip.getId(), "pai");
        trip = app.autoSchedule.execute(trip.getId());
        require(trip.getBookmarkedActivities().size() == 2, "bookmark use case");
        require(!trip.getScheduledEvents().isEmpty(), "auto schedule use case");
        require(app.summary.execute(trip.getId()).contains("Royal Ontario Museum"), "summary use case");
        require(app.distances.estimateTravelMinutes(app.activities.findById("rom").get().getLocation(),
                app.activities.findById("pai").get().getLocation(), TransportationMode.WALKING)
                > app.distances.estimateTravelMinutes(app.activities.findById("rom").get().getLocation(),
                app.activities.findById("pai").get().getLocation(), TransportationMode.DRIVING), "transport mode timing");
        System.out.println("All CloseAI tests passed.");
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("Failed: " + label);
    }
}
