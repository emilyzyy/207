package interface_adapter.places;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.OpeningHours;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
                        + "\"addr:street\":\"Culture Road\"}}]}");
        NominatimPlacesService service = service();

        assertEquals(1, service.search("Toronto", "museum").size());
        assertEquals(1, service.search("Toronto", "culture").size());
        assertTrue(service.search("Toronto", "restaurant").isEmpty());
    }

    @Test
    void prefersEnglishNameTagWhenPresent() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":124,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Tour Eiffel\",\"name:en\":\"Eiffel Tower\","
                        + "\"tourism\":\"attraction\"}}]}");

        List<Activity> results = service().search("Paris", "");

        assertEquals(1, results.size());
        assertEquals("Eiffel Tower", results.get(0).getName());
    }

    @Test
    void fallsBackToInternationalNameTag() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":125,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Музей\",\"int_name\":\"Museum\","
                        + "\"tourism\":\"museum\"}}]}");

        List<Activity> results = service().search("Moscow", "");

        assertEquals(1, results.size());
        assertEquals("Museum", results.get(0).getName());
    }

    @Test
    void keepsOriginalNameWithoutEnglishTags() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":126,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Café Local\",\"amenity\":\"cafe\"}}]}");

        List<Activity> results = service().search("Paris", "");

        assertEquals(1, results.size());
        assertEquals("Café Local", results.get(0).getName());
    }

    @Test
    void leavesEnglishNameUnchangedWhenEnglishTagMatches() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":127,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"City Museum\",\"name:en\":\"City Museum\","
                        + "\"tourism\":\"museum\"}}]}");

        List<Activity> results = service().search("Toronto", "");

        assertEquals(1, results.size());
        assertEquals("City Museum", results.get(0).getName());
    }

    @Test
    void derivesOpeningTimesFromOpeningHoursTag() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":300,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Cafe A\",\"amenity\":\"cafe\","
                        + "\"opening_hours\":\"Mo-Fr 09:30-17:15; Sa 10:00-14:00\"}}]}");

        List<Activity> results = service().search("Toronto", "");

        assertEquals(1, results.size());
        assertEquals(java.time.LocalTime.of(9, 30), results.get(0).getOpeningTime());
        assertEquals(java.time.LocalTime.of(17, 15), results.get(0).getClosingTime());
        assertEquals("Mo-Fr 09:30-17:15; Sa 10:00-14:00", results.get(0).getOpeningHoursText());
    }

    @Test
    void treats247AsAllDayWindow() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":301,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Cafe B\",\"amenity\":\"cafe\","
                        + "\"opening_hours\":\"24/7\"}}]}");

        List<Activity> results = service().search("Toronto", "");

        assertEquals(1, results.size());
        assertEquals(java.time.LocalTime.of(0, 0), results.get(0).getOpeningTime());
        assertEquals(java.time.LocalTime.of(23, 59), results.get(0).getClosingTime());
    }

    @Test
    void fallsBackToDefaultHoursWithoutOpeningHoursTag() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":302,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Cafe C\",\"amenity\":\"cafe\"}}]}");

        List<Activity> results = service().search("Toronto", "");

        assertEquals(1, results.size());
        assertEquals(java.time.LocalTime.of(9, 0), results.get(0).getOpeningTime());
        assertEquals(java.time.LocalTime.of(21, 0), results.get(0).getClosingTime());
        assertEquals(null, results.get(0).getOpeningHoursText());
    }

    @Test
    void fallsBackToDefaultHoursForUnparseableOpeningHours() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":303,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Cafe D\",\"amenity\":\"cafe\","
                        + "\"opening_hours\":\"by appointment only\"}}]}");

        List<Activity> results = service().search("Toronto", "");

        assertEquals(1, results.size());
        assertEquals(java.time.LocalTime.of(9, 0), results.get(0).getOpeningTime());
        assertEquals(java.time.LocalTime.of(21, 0), results.get(0).getClosingTime());
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

    @Test
    void returnsEmptyWhenOverpassReturnsHtmlErrorPage() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "<html><body><p><strong style=\"color:#FF0000\">Error</strong>: "
                        + "runtime error: ... too busy ...</p></body></html>");

        assertTrue(service().search("Toronto", "").isEmpty());
    }

    @Test
    void searchInBoundsParsesBoundingBoxElements() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":200,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Cafe A\",\"amenity\":\"cafe\"}},"
                        + "{\"id\":201,\"lat\":43.67,\"lon\":-79.38,"
                        + "\"tags\":{\"name\":\"Museum B\",\"tourism\":\"museum\"}},"
                        + "{\"id\":202,\"lat\":43.68,\"lon\":-79.37,"
                        + "\"tags\":{\"name\":\"Shop C\",\"shop\":\"supermarket\"}}]}");

        List<Activity> results =
                service().searchInBounds(43.6, -79.4, 43.7, -79.3, 100);

        assertEquals(3, results.size());
        assertEquals("osm-200", results.get(0).getId());
        assertEquals(ActivityCategory.COFFEE, results.get(0).getCategory());
        assertEquals("osm-201", results.get(1).getId());
        assertEquals("osm-202", results.get(2).getId());
    }

    @Test
    void searchInBoundsHonorsMaxResults() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":200,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Cafe A\",\"amenity\":\"cafe\"}},"
                        + "{\"id\":201,\"lat\":43.67,\"lon\":-79.38,"
                        + "\"tags\":{\"name\":\"Cafe B\",\"amenity\":\"cafe\"}}]}");

        List<Activity> result =
                service().searchInBounds(43.6, -79.4, 43.7, -79.3, 1);

        assertEquals(1, result.size());
        assertEquals("osm-200", result.get(0).getId());
    }

    @Test
    void mapsExpandedOsmTagsToNewAndExistingCategories() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":["
                        + element(401, "Cinema", "\"amenity\":\"cinema\"") + ","
                        + element(402, "Park", "\"leisure\":\"park\"") + ","
                        + element(403, "Fort", "\"historic\":\"fort\"") + ","
                        + element(404, "Pool", "\"leisure\":\"swimming_pool\"") + ","
                        + element(405, "Gallery", "\"tourism\":\"gallery\"") + ","
                        + element(406, "Bakery", "\"shop\":\"bakery\"") + ","
                        + element(407, "Tea Shop", "\"shop\":\"tea\"") + ","
                        + element(408, "Science Museum", "\"museum\":\"science\"") + ","
                        + element(409, "Trail", "\"highway\":\"trailhead\"") + ","
                        + element(410, "Book Shop", "\"shop\":\"books\"") + ","
                        + element(411, "Aquarium", "\"tourism\":\"aquarium\"")
                        + "]}");

        List<Activity> results = service().search("Toronto", "");

        assertEquals(ActivityCategory.ENTERTAINMENT, results.get(0).getCategory());
        assertEquals(ActivityCategory.PARKS_NATURE, results.get(1).getCategory());
        assertEquals(ActivityCategory.HISTORIC, results.get(2).getCategory());
        assertEquals(ActivityCategory.SPORTS_RECREATION, results.get(3).getCategory());
        assertEquals(ActivityCategory.ARTS_CULTURE, results.get(4).getCategory());
        assertEquals(ActivityCategory.FOOD, results.get(5).getCategory());
        assertEquals(ActivityCategory.COFFEE, results.get(6).getCategory());
        assertEquals(ActivityCategory.MUSEUM, results.get(7).getCategory());
        assertEquals(ActivityCategory.PARKS_NATURE, results.get(8).getCategory());
        assertEquals(ActivityCategory.SHOPPING, results.get(9).getCategory());
        assertEquals(ActivityCategory.ATTRACTION, results.get(10).getCategory());
    }

    @Test
    void parsesCenterCoordinatesForAreaActivities() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"type\":\"way\",\"id\":500,"
                        + "\"center\":{\"lat\":43.70,\"lon\":-79.40},"
                        + "\"tags\":{\"name\":\"Large Park\",\"leisure\":\"park\"}}]}");

        List<Activity> results = service().search("Toronto", "");

        assertEquals(1, results.size());
        assertEquals(ActivityCategory.PARKS_NATURE, results.get(0).getCategory());
        assertEquals(43.70, results.get(0).getLocation().getLatitude());
        assertEquals(-79.40, results.get(0).getLocation().getLongitude());
    }

    @Test
    void cacheMissSearchesNominatimAndFetchesExactOsmObject() throws Exception {
        AtomicInteger geocodingCalls = new AtomicInteger();
        AtomicInteger overpassCalls = new AtomicInteger();
        AtomicReference<String> exactQuery = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            String body = geocodingCalls.getAndIncrement() == 0
                    ? "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]"
                    : "[{\"lat\":\"43.647\",\"lon\":\"-79.414\","
                            + "\"osm_type\":\"relation\",\"osm_id\":16751345}]";
            respond(exchange, 200, body);
        });
        server.createContext("/interpreter", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            int call = overpassCalls.getAndIncrement();
            if (call > 0) exactQuery.set(java.net.URLDecoder.decode(
                    request.substring("data=".length()), StandardCharsets.UTF_8));
            String body = call == 0
                    ? "{\"elements\":[" + element(600, "Nearby Cafe",
                            "\"amenity\":\"cafe\"") + "]}"
                    : "{\"elements\":[{\"type\":\"relation\",\"id\":16751345,"
                            + "\"center\":{\"lat\":43.647,\"lon\":-79.414},"
                            + "\"tags\":{\"name\":\"Trinity Bellwoods Park\","
                            + "\"leisure\":\"park\"}}]}";
            respond(exchange, 200, body);
        });
        server.start();

        List<Activity> results = service().search("Toronto", "Trinity Bellwoods Park");

        assertEquals(1, results.size());
        assertEquals("Trinity Bellwoods Park", results.get(0).getName());
        assertEquals(ActivityCategory.PARKS_NATURE, results.get(0).getCategory());
        assertTrue(exactQuery.get().contains("relation(16751345)"));
    }

    private static String element(long id, String name, String extraTags) {
        return "{\"id\":" + id + ",\"lat\":43.66,\"lon\":-79.39,\"tags\":{"
                + "\"name\":\"" + name + "\"," + extraTags + "}}";
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

    /**
     * The two readings of one tag, and how they divide the work.
     *
     * <p>{@code deriveOpenClose} flattens the whole week into a single window so that every
     * caller always has something valid: for this tag it reports 09:00-23:00, because 09:00
     * is the earliest opening anywhere in the week and 23:00 the latest closing. That is a
     * deliberately generous reading and it is wrong about Saturday, when the venue does not
     * open until 11:00.</p>
     *
     * <p>The parsed {@link OpeningHours} keeps the weekdays apart, which is what the
     * scheduler consults. Both live on the Activity because both are useful — the flat
     * window for anything that only knows about one, the per-weekday reading for placing a
     * visit — and this test pins the difference so neither can quietly become the other.</p>
     */
    @Test
    void theFlattenedWindowAndTheParsedWeekAreBothKeptAndDoNotAgree() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":400,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Late Bar\",\"amenity\":\"cafe\","
                        + "\"opening_hours\":\"Mo-Fr 09:00-17:00; Sa-Su 11:00-23:00\"}}]}");

        Activity activity = service().search("Toronto", "").get(0);

        assertEquals(LocalTime.of(9, 0), activity.getOpeningTime());
        assertEquals(LocalTime.of(23, 0), activity.getClosingTime());

        // Saturday, 15 August 2026: the flat window says 09:00, the truth is 11:00.
        assertEquals(LocalTime.of(11, 0), activity.getOpeningHours()
                .intervalsOn(LocalDate.of(2026, 8, 15)).get(0).getStart());
        // Wednesday: the flat window says 23:00, the truth is 17:00.
        assertEquals(LocalTime.of(17, 0), activity.getOpeningHours()
                .intervalsOn(LocalDate.of(2026, 8, 12)).get(0).getEnd());
    }

    /**
     * Panning the map is now a production source of activities, and it goes through the same
     * parseElements. Hours must arrive by that route too, or a place added from the map would
     * be scheduled on different rules from the same place found by search.
     */
    @Test
    void hoursArriveThroughTheBoundingBoxSearchAsWellAsTheTextSearch() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":401,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Museum B\",\"tourism\":\"museum\","
                        + "\"opening_hours\":\"We 10:00-13:00,14:00-18:00\"}}]}");

        Activity activity = service().searchInBounds(43.6, -79.4, 43.7, -79.3, 100).get(0);

        assertEquals("We 10:00-13:00,14:00-18:00", activity.getOpeningHoursText());
        assertEquals(2, activity.getOpeningHours()
                        .intervalsOn(LocalDate.of(2026, 8, 12)).size(),
                "a venue that shuts for lunch has two shifts, not one long day");
    }

    /** The raw text is kept verbatim, because it is the only thing we can show a user. */
    @Test
    void theProvidersOwnWordsAreKeptUnmodifiedBesideTheParsedReading() throws Exception {
        startServer(
                200,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]",
                200,
                "{\"elements\":[{\"id\":402,\"lat\":43.66,\"lon\":-79.39,"
                        + "\"tags\":{\"name\":\"Odd Place\",\"tourism\":\"museum\","
                        + "\"opening_hours\":\"Mo-Su sunrise-sunset\"}}]}");

        Activity activity = service().search("Toronto", "").get(0);

        assertEquals("Mo-Su sunrise-sunset", activity.getOpeningHoursText(),
                "we could not parse it, but we can still show the user what it said");
        assertFalse(activity.getOpeningHours().isKnown());
    }
}
