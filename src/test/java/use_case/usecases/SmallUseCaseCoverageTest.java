package use_case.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import database.persistence.CachedPlacesRepository;
import database.persistence.InMemoryTripRepository;
import entity.entities.Activity;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.WeatherSeverity;
import use_case.ports.PlacesService;

/** Direct coverage for small use cases that previously lacked dedicated tests. */
final class SmallUseCaseCoverageTest {

    @Test
    void listTripsReturnsRepositoryContents() {
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(sampleTrip("a"));
        trips.save(sampleTrip("b"));

        final List<Trip> listed = new ListTripsUseCase(trips).execute();

        assertEquals(2, listed.size());
    }

    @Test
    void discoverRecordsPlacesFromSearch() {
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(sampleTrip("trip-1"));
        final CachedPlacesRepository writer = new CachedPlacesRepository();
        final Activity museum = activity("museum");
        final PlacesService places = (destination, query) -> Collections.singletonList(museum);

        final Trip updated = new DiscoverTripPlacesUseCase(trips, places, writer)
                .execute("trip-1", "Toronto");

        assertEquals(1, updated.getDiscoveredPlaces().size());
        assertTrue(writer.findById("museum").isPresent());
    }

    @Test
    void discoverEmptySearchLeavesTripUnchanged() {
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        final Trip trip = sampleTrip("trip-1");
        trips.save(trip);
        final PlacesService places = (destination, query) -> Collections.emptyList();

        final Trip result = new DiscoverTripPlacesUseCase(trips, places, new CachedPlacesRepository())
                .execute("trip-1", "Nowhere");

        assertSame(trip, result);
        assertTrue(result.getDiscoveredPlaces().isEmpty());
    }

    @Test
    void discoverRecordWithEmptyListIsNoOp() {
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(sampleTrip("trip-1"));
        final DiscoverTripPlacesUseCase useCase = new DiscoverTripPlacesUseCase(
                trips, (d, q) -> Collections.emptyList(), new CachedPlacesRepository());

        final Trip result = useCase.record("trip-1", Collections.emptyList());
        assertTrue(result.getDiscoveredPlaces().isEmpty());
    }

    @Test
    void discoverMissingTripFails() {
        final DiscoverTripPlacesUseCase useCase = new DiscoverTripPlacesUseCase(
                new InMemoryTripRepository(),
                (d, q) -> Collections.singletonList(activity("x")),
                new CachedPlacesRepository());

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("missing", "Toronto"));
    }

    @Test
    void getWeatherWarningAndHourly() {
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(sampleTrip("trip-1"));
        final WeatherWarning warning = new WeatherWarning(
                new Location(43.6, -79.3, "Toronto"), LocalTime.of(9, 0),
                "Clear", WeatherSeverity.LOW, "Nice");
        final GetWeatherWarningUseCase useCase = new GetWeatherWarningUseCase(
                trips, ignored -> Collections.singletonList(warning));

        assertEquals("Clear", useCase.execute("trip-1").getWeatherCondition());
        assertEquals(1, useCase.executeHourly("trip-1").size());
    }

    @Test
    void getWeatherWarningMissingTripFails() {
        final GetWeatherWarningUseCase useCase = new GetWeatherWarningUseCase(
                new InMemoryTripRepository(), ignored -> Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("missing"));
        assertThrows(IllegalArgumentException.class, () -> useCase.executeHourly("missing"));
    }

    @Test
    void addActivityToPlanWithPreferredStart() {
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(sampleTrip("trip-1"));
        final CachedPlacesRepository activities = new CachedPlacesRepository();
        activities.addAll(Collections.singletonList(activity("museum")));

        final Trip updated = new AddActivityToPlanUseCase(trips, activities)
                .execute("trip-1", "museum", LocalTime.of(11, 0));

        assertEquals(1, updated.getScheduledEvents().size());
        assertEquals(LocalTime.of(11, 0), updated.getScheduledEvents().get(0).getStartTime());
    }

    @Test
    void addActivityToPlanUsesNextAvailableWhenStartNull() {
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(sampleTrip("trip-1"));
        final CachedPlacesRepository activities = new CachedPlacesRepository();
        activities.addAll(Collections.singletonList(activity("museum")));

        final Trip updated = new AddActivityToPlanUseCase(trips, activities)
                .execute("trip-1", "museum", null);

        assertEquals(LocalTime.of(9, 0), updated.getScheduledEvents().get(0).getStartTime());
    }

    @Test
    void addActivityToPlanRejectsBadWindowAndMissingIds() {
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(sampleTrip("trip-1"));
        final CachedPlacesRepository activities = new CachedPlacesRepository();
        activities.addAll(Collections.singletonList(activity("museum")));
        final AddActivityToPlanUseCase useCase = new AddActivityToPlanUseCase(trips, activities);

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("missing", "museum", LocalTime.of(10, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("trip-1", "missing", LocalTime.of(10, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> {
                    useCase.execute("trip-1", "museum",
                            LocalTime.of(12, 0), LocalTime.of(11, 0));
                });
    }

    @Test
    void removeScheduledEventRejectsMissingTripOrEvent() {
        final InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(sampleTrip("trip-1"));
        final RemoveScheduledEventUseCase useCase = new RemoveScheduledEventUseCase(trips);

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("missing", "evt"));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("trip-1", "missing-event"));
    }

    private static Trip sampleTrip(String id) {
        return new Trip(id, "Toronto", LocalDate.of(2026, 8, 9),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
    }

    private static Activity activity(String id) {
        return new Activity(id, "Place " + id, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(9, 0), LocalTime.of(17, 0), IndoorOutdoorType.INDOOR, "low");
    }
}
