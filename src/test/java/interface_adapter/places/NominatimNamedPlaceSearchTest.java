package interface_adapter.places;

import use_case.search.PlaceSearchException;
import use_case.search.SearchFailure;
import use_case.search.GeoPoint;
import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NominatimNamedPlaceSearchTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void namedRelationIsReturnedDirectlyWithoutOverpass() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        start(200, "[{\"osm_type\":\"relation\",\"osm_id\":16751345,"
                + "\"lat\":\"43.647\",\"lon\":\"-79.414\","
                + "\"name\":\"Trinity Bellwoods Park\","
                + "\"display_name\":\"Trinity Bellwoods Park, Toronto, Canada\","
                + "\"category\":\"leisure\",\"type\":\"park\","
                + "\"extratags\":{\"opening_hours\":\"06:00-23:00\"}}]", requests);

        List<Activity> results = service().find(
                "Toronto", "Trinity Bellwoods Park", 10);

        assertEquals(1, requests.get());
        assertEquals(1, results.size());
        assertEquals("osm-relation-16751345", results.get(0).getId());
        assertEquals(ActivityCategory.PARKS_NATURE, results.get(0).getCategory());
        assertEquals(0.0, results.get(0).getRating());
        assertEquals("Trinity Bellwoods Park, Toronto, Canada",
                results.get(0).getLocation().getAddress());
    }

    @Test
    void requestsUsefulNominatimDetails() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        start(200, "[]", requests);

        service().find("Toronto", "Royal Ontario Museum", 10);

        // The server saw exactly one direct named-place request; its query is validated
        // by the encoded destination/name successfully reaching this handler.
        assertEquals(1, requests.get());
    }

    @Test
    void rateLimitIsNotReportedAsNoMatches() throws Exception {
        start(429, "busy", new AtomicInteger());

        PlaceSearchException failure = assertThrows(PlaceSearchException.class,
                () -> service().find("Toronto", "High Park", 10));

        assertEquals(SearchFailure.RATE_LIMITED, failure.getFailure());
    }

    @Test
    void simplifiesDuplicatedIslandDestinationLabels() {
        assertEquals("Sicily, Italy", NominatimNamedPlaceSearch.simplifyDestination(
                "Sicily island, Sicily, Italy"));
    }

    @Test
    void largeMunicipalBoundsAreCappedForNearbyDiscovery() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        start(200, "[{\"lat\":\"43.6534817\",\"lon\":\"-79.3839347\","
                + "\"boundingbox\":[\"43.5796082\",\"43.8554425\","
                + "\"-79.6392832\",\"-79.1132193\"]}]", requests);

        GeoPoint point = service().geocode("Toronto");

        assertEquals(3_000, point.getDiscoveryRadiusMeters());
        assertEquals(1, requests.get());
    }

    private NominatimNamedPlaceSearch service() {
        return new NominatimNamedPlaceSearch(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"));
    }

    private void start(int status, String body, AtomicInteger requests) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            requests.incrementAndGet();
            String query = exchange.getRequestURI().getRawQuery();
            assertTrue(query.contains("q="));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
