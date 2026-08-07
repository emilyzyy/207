package closeai.infrastructure.places;

import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.OpeningHours;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NominatimPlacesServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsGeocodingAndOverpassResponses() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":123,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"City Museum\",\"tourism\":\"museum\","
                        + "\"addr:street\":\"Museum Road\"}}]}");

        List<Activity> results = service().search("Toronto", "");

        assertEquals(1, results.size());
        assertEquals("osm-123", results.get(0).getId());
        assertEquals("City Museum", results.get(0).getName());
        assertEquals(ActivityCategory.MUSEUM, results.get(0).getCategory());
        assertEquals("Museum Road", results.get(0).getLocation().getAddress());
    }

    @Test
    void appliesQueryToDiscoveredNamesCategoriesAndAddresses() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":123,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"City Museum\",\"tourism\":\"museum\","
                        + "\"addr:street\":\"Culture Road\"}}]}" );
        NominatimPlacesService service = service();

        assertEquals(1, service.search("Toronto", "museum").size());
        assertEquals(1, service.search("Toronto", "culture").size());
        assertTrue(service.search("Toronto", "restaurant").isEmpty());
    }

    @Test
    void returnsEmptyWhenGeocodingHasNoResult() throws Exception {
        startServer(200, "[]", 200, "{\"elements\":[]}");

        assertTrue(service().search("Unknown", "").isEmpty());
    }

    @Test
    void returnsEmptyForNonSuccessfulHttpStatus() throws Exception {
        startServer(503, "unavailable", 200, "{\"elements\":[]}");

        assertTrue(service().search("Toronto", "").isEmpty());
    }

    @Test
    void returnsEmptyForMalformedOverpassJson() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{not-json");

        assertTrue(service().search("Toronto", "").isEmpty());
    }

    private void startServer(
            int geocodingStatus,
            String geocodingBody,
            int overpassStatus,
            String overpassBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange ->
                respond(exchange, geocodingStatus, geocodingBody));
        server.createContext("/interpreter", exchange ->
                respond(exchange, overpassStatus, overpassBody));
        server.start();
    }

    private NominatimPlacesService service() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new NominatimPlacesService(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                URI.create(base + "/search"),
                URI.create(base + "/interpreter"));
    }

    private static void respond(
            HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    // --- opening hours, from the tag Overpass already returns ---------------------------

    /**
     * The Overpass query asks for "out body", so every tag is in the response and the
     * opening_hours tag has always been sitting there unread. This is the test that it now
     * reaches an Activity rather than being dropped on the floor.
     */
    @Test
    void readsRealOpeningHoursFromTheOverpassResponse() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":123,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"City Museum\",\"tourism\":\"museum\","
                        + "\"opening_hours\":\"Mo-Fr 10:00-17:00; Sa-Su 11:00-16:00\"}}]}");

        OpeningHours hours = service().search("Toronto", "").get(0).getOpeningHours();

        assertTrue(hours.isKnown());
        // 12 August 2026 is a Wednesday, 15 August a Saturday.
        assertEquals(LocalTime.of(10, 0),
                hours.intervalsOn(LocalDate.of(2026, 8, 12)).get(0).getStart());
        assertEquals(LocalTime.of(16, 0),
                hours.intervalsOn(LocalDate.of(2026, 8, 15)).get(0).getEnd());
    }

    @Test
    void aVenueWithNoOpeningHoursTagIsUnknownRatherThanClosed() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":123,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"City Museum\",\"tourism\":\"museum\"}}]}");

        Activity activity = service().search("Toronto", "").get(0);

        assertFalse(activity.getOpeningHours().isKnown());
        assertFalse(activity.getOpeningHours().isClosedOn(LocalDate.of(2026, 8, 12)),
                "most OSM places have no hours, and none of them are therefore shut");
    }

    @Test
    void anUnparseableOpeningHoursTagDegradesToUnknownRatherThanFailingTheSearch() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":123,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"City Museum\",\"tourism\":\"museum\","
                        + "\"opening_hours\":\"whenever the curator feels like it\"}}]}");

        List<Activity> results = service().search("Toronto", "");

        assertEquals(1, results.size(), "a tag we cannot read must not lose the place");
        assertFalse(results.get(0).getOpeningHours().isKnown());
    }

    @Test
    void theCoarseFallbackWindowStillExistsForCodeThatOnlyKnowsAboutOne() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":123,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"City Museum\",\"tourism\":\"museum\"}}]}");

        Activity activity = service().search("Toronto", "").get(0);

        assertEquals(LocalTime.of(9, 0), activity.getOpeningTime());
        assertEquals(LocalTime.of(21, 0), activity.getClosingTime());
    }
}
