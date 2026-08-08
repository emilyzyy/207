package trippy;

import trippy.application.AppContainer;
import trippy.application.usecases.CreateTripInputData;
import trippy.domain.entities.Trip;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class TestRunner {
    public static void main(String[] args) {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = app.createTrip.execute(new CreateTripInputData(
                "Toronto", LocalDate.of(2026, 7, 18),
                LocalTime.of(9, 0), LocalTime.of(19, 0),
                TransportationMode.TRANSIT));
        app.bookmarkActivity.execute(trip.getId(), "rom");
        app.bookmarkActivity.execute(trip.getId(), "pai");
        trip = app.autoSchedule.execute(trip.getId());
        require(trip.getBookmarkedActivities().size() == 2, "bookmark use case");
        require(!trip.getScheduledEvents().isEmpty(), "auto schedule use case");
        require(app.summary.execute(trip.getId()).contains("Royal Ontario Museum"), "summary use case");
        LocalDateTime departure = LocalDateTime.of(LocalDate.of(2026, 7, 18), LocalTime.NOON);
        Location rom = app.activities.findById("rom").get().getLocation();
        Location pai = app.activities.findById("pai").get().getLocation();
        require(app.distances.estimateTravelMinutes(rom, pai, TransportationMode.WALKING, departure)
                > app.distances.estimateTravelMinutes(rom, pai, TransportationMode.DRIVING, departure),
                "transport mode timing");
        System.out.println("All Trippy tests passed.");
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("Failed: " + label);
    }
}
