package interface_adapter.places;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import use_case.ports.NearbyActivityDiscovery;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.PlaceSearchException;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;

final class OpenStreetMapPlacesServiceTest {
    @Test
    void namedSearchUsesNominatimWithoutCallingNearbyDiscovery() {
        final AtomicInteger namedCalls = new AtomicInteger();
        final AtomicInteger nearbyCalls = new AtomicInteger();
        final OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> {
                    namedCalls.incrementAndGet();
                    return List.of(activity("osm-relation-1", "Royal Ontario Museum",
                            ActivityCategory.MUSEUM));
                }, nearby(nearbyCalls));

        final ActivitySearchResult result = service.search(request("Royal Ontario Museum"));

        assertEquals(1, namedCalls.get());
        assertEquals(0, nearbyCalls.get());
        assertEquals(SearchSource.NOMINATIM, result.getSource());
        assertEquals("Royal Ontario Museum", result.getActivities().get(0).getName());
    }

    @Test
    void exactCachedNameAvoidsAnotherRemoteRequest() {
        final AtomicInteger calls = new AtomicInteger();
        final OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> {
                    calls.incrementAndGet();
                    return List.of(activity("osm-node-2", "Tim Hortons",
                            ActivityCategory.COFFEE));
                }, nearby(new AtomicInteger()));

        service.search(request("Tim Hortons"));
        final ActivitySearchResult second = service.search(request("Tim Hortons"));

        assertEquals(1, calls.get());
        assertEquals(SearchSource.LOCAL, second.getSource());
    }

    @Test
    void networkFailureIsDistinctFromNoMatchesAndKeepsLocalResults() {
        final AtomicInteger calls = new AtomicInteger();
        final OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> {
                    if (calls.getAndIncrement() == 0) {
                        return List.of(
                                activity("osm-node-3", "High Park", ActivityCategory.PARKS_NATURE));
                    }
                    throw new PlaceSearchException(SearchFailure.RATE_LIMITED, "busy");
                }, nearby(new AtomicInteger()));
        service.search(request("High Park"));

        final ActivitySearchResult result = service.search(request("High"));

        assertEquals(SearchFailure.RATE_LIMITED, result.getFailure());
        assertTrue(result.isPartial());
        assertEquals(1, result.getActivities().size());
    }

    @Test
    void accentsAndMultiWordNamesAreNormalizedForLocalRanking() {
        final OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> List.of(
                        activity("osm-node-4", "Musée Royal", ActivityCategory.MUSEUM)),
                nearby(new AtomicInteger()));
        service.search(request("Musée Royal"));

        final ActivitySearchResult result = service.search(request("musee royal"));

        assertEquals(SearchSource.LOCAL, result.getSource());
        assertEquals("Musée Royal", result.getActivities().get(0).getName());
    }

    @Test
    void placesLoadedByTheMapAreImmediatelySearchableLocally() {
        final AtomicInteger namedCalls = new AtomicInteger();
        final Activity park = activity("osm-way-5", "Christie Pits Park",
                ActivityCategory.PARKS_NATURE);
        final NearbyActivityDiscovery nearby = new NearbyActivityDiscovery() {
            @Override public List<Activity> around(String destination, int limit) {
                return List.of();
            }

            @Override public List<Activity> inBounds(double south, double west, double north,
                                                     double east, int limit) {
                return List.of(park);
            }
        };
        final OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> {
                    namedCalls.incrementAndGet();
                    return List.of();
                }, nearby);
        service.searchInBounds("Toronto", 43.6, -79.5, 43.7, -79.3, 50);

        final ActivitySearchResult result = service.search(request("Christie Pits Park"));

        assertEquals(SearchSource.LOCAL, result.getSource());
        assertEquals(0, namedCalls.get());
        assertEquals(park.getId(), result.getActivities().get(0).getId());
    }

    @Test
    void viewportPlacesLoadedForOneTripDoNotLeakIntoAnotherDestination() {
        final Activity torontoPark = activity("osm-way-6", "High Park",
                ActivityCategory.PARKS_NATURE);
        final NearbyActivityDiscovery nearby = new NearbyActivityDiscovery() {
            @Override public List<Activity> around(String destination, int limit) {
                return List.of();
            }

            @Override public List<Activity> inBounds(double south, double west, double north,
                                                     double east, int limit) {
                return List.of(torontoPark);
            }
        };
        final OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> List.of(), nearby);

        service.searchInBounds("Toronto", 43.6, -79.5, 43.7, -79.3, 50);

        final ActivitySearchResult vancouver = service.search(
                new ActivitySearchRequest("Vancouver", "", null, null, 25));
        assertTrue(vancouver.getActivities().stream()
                .noneMatch(activity -> activity.getId().equals(torontoPark.getId())));
    }

    private static ActivitySearchRequest request(String query) {
        return new ActivitySearchRequest("Toronto", query, null, null, 25);
    }

    private static NearbyActivityDiscovery nearby(AtomicInteger calls) {
        return new NearbyActivityDiscovery() {
            @Override public List<Activity> around(String destination, int limit) {
                calls.incrementAndGet();
                return List.of();
            }

            @Override public List<Activity> inBounds(double south, double west, double north,
                                                     double east, int limit) {
                calls.incrementAndGet();
                return List.of();
            }
        };
    }

    private static Activity activity(String id, String name, ActivityCategory category) {
        return new Activity(id, name, category,
                new Location(43.65, -79.38, name + ", Toronto"), 0.0, 60,
                LocalTime.of(9, 0), LocalTime.of(21, 0),
                category == ActivityCategory.PARKS_NATURE
                        ? IndoorOutdoorType.OUTDOOR : IndoorOutdoorType.INDOOR,
                "Low");
    }
}
