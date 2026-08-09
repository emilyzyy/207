package interface_adapter.places;

import use_case.ports.NearbyActivityDiscovery;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.PlaceSearchException;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;
import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OpenStreetMapPlacesServiceTest {
    @Test
    void namedSearchUsesNominatimWithoutCallingNearbyDiscovery() {
        AtomicInteger namedCalls = new AtomicInteger();
        AtomicInteger nearbyCalls = new AtomicInteger();
        OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> {
                    namedCalls.incrementAndGet();
                    return List.of(activity("osm-relation-1", "Royal Ontario Museum",
                            ActivityCategory.MUSEUM));
                }, nearby(nearbyCalls));

        ActivitySearchResult result = service.search(request("Royal Ontario Museum"));

        assertEquals(1, namedCalls.get());
        assertEquals(0, nearbyCalls.get());
        assertEquals(SearchSource.NOMINATIM, result.getSource());
        assertEquals("Royal Ontario Museum", result.getActivities().get(0).getName());
    }

    @Test
    void exactCachedNameAvoidsAnotherRemoteRequest() {
        AtomicInteger calls = new AtomicInteger();
        OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> {
                    calls.incrementAndGet();
                    return List.of(activity("osm-node-2", "Tim Hortons",
                            ActivityCategory.COFFEE));
                }, nearby(new AtomicInteger()));

        service.search(request("Tim Hortons"));
        ActivitySearchResult second = service.search(request("Tim Hortons"));

        assertEquals(1, calls.get());
        assertEquals(SearchSource.LOCAL, second.getSource());
    }

    @Test
    void networkFailureIsDistinctFromNoMatchesAndKeepsLocalResults() {
        AtomicInteger calls = new AtomicInteger();
        OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> {
                    if (calls.getAndIncrement() == 0) return List.of(
                            activity("osm-node-3", "High Park", ActivityCategory.PARKS_NATURE));
                    throw new PlaceSearchException(SearchFailure.RATE_LIMITED, "busy");
                }, nearby(new AtomicInteger()));
        service.search(request("High Park"));

        ActivitySearchResult result = service.search(request("High"));

        assertEquals(SearchFailure.RATE_LIMITED, result.getFailure());
        assertTrue(result.isPartial());
        assertEquals(1, result.getActivities().size());
    }

    @Test
    void accentsAndMultiWordNamesAreNormalizedForLocalRanking() {
        OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> List.of(
                        activity("osm-node-4", "Musée Royal", ActivityCategory.MUSEUM)),
                nearby(new AtomicInteger()));
        service.search(request("Musée Royal"));

        ActivitySearchResult result = service.search(request("musee royal"));

        assertEquals(SearchSource.LOCAL, result.getSource());
        assertEquals("Musée Royal", result.getActivities().get(0).getName());
    }

    @Test
    void placesLoadedByTheMapAreImmediatelySearchableLocally() {
        AtomicInteger namedCalls = new AtomicInteger();
        Activity park = activity("osm-way-5", "Christie Pits Park",
                ActivityCategory.PARKS_NATURE);
        NearbyActivityDiscovery nearby = new NearbyActivityDiscovery() {
            @Override public List<Activity> around(String destination, int limit) {
                return List.of();
            }
            @Override public List<Activity> inBounds(double south, double west, double north,
                                                     double east, int limit) {
                return List.of(park);
            }
        };
        OpenStreetMapPlacesService service = new OpenStreetMapPlacesService(
                (destination, query, limit) -> {
                    namedCalls.incrementAndGet(); return List.of();
                }, nearby);
        service.searchInBounds(43.6, -79.5, 43.7, -79.3, 50);

        ActivitySearchResult result = service.search(request("Christie Pits Park"));

        assertEquals(SearchSource.LOCAL, result.getSource());
        assertEquals(0, namedCalls.get());
        assertEquals(park.getId(), result.getActivities().get(0).getId());
    }

    private static ActivitySearchRequest request(String query) {
        return new ActivitySearchRequest("Toronto", query, null, null, 25);
    }

    private static NearbyActivityDiscovery nearby(AtomicInteger calls) {
        return new NearbyActivityDiscovery() {
            @Override public List<Activity> around(String destination, int limit) {
                calls.incrementAndGet(); return List.of();
            }
            @Override public List<Activity> inBounds(double south, double west, double north,
                                                     double east, int limit) {
                calls.incrementAndGet(); return List.of();
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
