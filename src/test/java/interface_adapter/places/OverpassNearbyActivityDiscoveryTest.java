package interface_adapter.places;

import entity.valueobjects.GeoPoint;
import use_case.search.PlaceSearchException;
import use_case.search.SearchFailure;
import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OverpassNearbyActivityDiscoveryTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void discoversNodesWaysAndRelationsWithTypeSafeIds() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        start(200, "{\"elements\":["
                + "{\"type\":\"node\",\"id\":12,\"lat\":43.1,\"lon\":-79.1,"
                + "\"tags\":{\"name\":\"Cafe\",\"amenity\":\"cafe\"}},"
                + "{\"type\":\"way\",\"id\":12,\"center\":{\"lat\":43.2,\"lon\":-79.2},"
                + "\"tags\":{\"name\":\"Museum\",\"tourism\":\"museum\"}},"
                + "{\"type\":\"relation\",\"id\":12,\"center\":{\"lat\":43.3,\"lon\":-79.3},"
                + "\"tags\":{\"name\":\"Park\",\"leisure\":\"park\"}}]}", query);

        List<Activity> results = service().around("Toronto", 25);

        assertEquals(List.of("osm-node-12", "osm-way-12", "osm-relation-12"),
                results.stream().map(Activity::getId)
                        .collect(java.util.stream.Collectors.toList()));
        assertEquals(ActivityCategory.PARKS_NATURE, results.get(2).getCategory());
        assertTrue(query.get().contains("nwr"));
        assertTrue(query.get().contains("out center"));
    }

    @Test
    void unavailableServiceProducesExplicitFailure() throws Exception {
        start(503, "busy", new AtomicReference<>());

        PlaceSearchException failure = assertThrows(PlaceSearchException.class,
                () -> service().around("Toronto", 25));

        assertEquals(SearchFailure.SERVICE_UNAVAILABLE, failure.getFailure());
    }

    @Test
    void emptyResponseIsRetriedInsteadOfBeingCachedForTheSession() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interpreter", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = "{\"elements\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        OverpassNearbyActivityDiscovery discovery = service();
        assertTrue(discovery.around("Toronto", 25).isEmpty());
        assertTrue(discovery.around("Toronto", 25).isEmpty());

        assertEquals(2, requests.get());
    }

    @Test
    void serverSideTimeoutRemarkIsTreatedAsAFailureAndTriesTheNextEndpoint() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interpreter", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            String body;
            if (requests.get() == 1) {
                body = "{\"remark\":\"runtime error: Query timed out in \\\"query\\\" after 11 seconds.\","
                        + "\"elements\":[]}";
            } else {
                body = "{\"elements\":[{\"type\":\"node\",\"id\":42,\"lat\":43.65,\"lon\":-79.38,"
                        + "\"tags\":{\"name\":\"Recovered Cafe\",\"amenity\":\"cafe\"}}]}";
                query.set("second");
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        URI endpoint = URI.create("http://127.0.0.1:"
                + server.getAddress().getPort() + "/interpreter");
        OverpassNearbyActivityDiscovery discovery = new OverpassNearbyActivityDiscovery(
                HttpClient.newHttpClient(), new ObjectMapper(),
                destination -> new GeoPoint(43.65, -79.38), List.of(endpoint, endpoint));

        List<Activity> results = discovery.around("Toronto", 25);

        assertEquals(2, requests.get());
        assertEquals(1, results.size());
        assertEquals("osm-node-42", results.get(0).getId());
    }

    @Test
    void placesPerWindowAreCappedAtTwentyFiveEvenWhenAskedForMore() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        start(200, "{\"elements\":[{\"type\":\"node\",\"id\":7,\"lat\":43.65,\"lon\":-79.38,"
                + "\"tags\":{\"name\":\"Park\",\"leisure\":\"park\"}}]}", query);

        service().around("Toronto", 100);

        assertTrue(query.get().contains("out center 25;"));
    }

    @Test
    void discoveryRadiusIsClampedToTheReliableOverpassCeiling() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        start(200, "{\"elements\":[{\"type\":\"node\",\"id\":7,\"lat\":43.65,\"lon\":-79.38,"
                + "\"tags\":{\"name\":\"Park\",\"leisure\":\"park\"}}]}", query);

        URI endpoint = URI.create("http://127.0.0.1:"
                + server.getAddress().getPort() + "/interpreter");
        OverpassNearbyActivityDiscovery discovery = new OverpassNearbyActivityDiscovery(
                HttpClient.newHttpClient(), new ObjectMapper(),
                destination -> new GeoPoint(43.65, -79.38, 3_000), List.of(endpoint));

        List<Activity> results = discovery.around("Toronto", 25);

        assertEquals(1, results.size());
        assertTrue(query.get().contains("around:1500,43.65,-79.38"));
    }

    @Test
    void viewportBoxIsLoadedByOneAroundQueryAtItsCentreAndCached() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interpreter", exchange -> {
            requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            query.set(java.net.URLDecoder.decode(body.substring("data=".length()),
                    StandardCharsets.UTF_8));
            byte[] bytes = ("{\"elements\":[{\"type\":\"node\",\"id\":7,\"lat\":43.65,"
                    + "\"lon\":-79.38,\"tags\":{\"name\":\"Park\",\"leisure\":\"park\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        OverpassNearbyActivityDiscovery discovery = service();
        List<Activity> first = discovery.inBounds(43.6, -79.5, 43.7, -79.3, 25);
        List<Activity> second = discovery.inBounds(43.6, -79.5, 43.7, -79.3, 25);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(1, requests.get());
        assertTrue(query.get().contains("around:1500,"));
        assertTrue(query.get().contains("out center"));
    }

    @Test
    void boxesFullyInsideTheWholeCityDiscoveryAreServedFromCoverageWithoutNewRequests() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interpreter", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = ("{\"elements\":[{\"type\":\"node\",\"id\":7,\"lat\":43.65,"
                    + "\"lon\":-79.38,\"tags\":{\"name\":\"Park\",\"leisure\":\"park\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        OverpassNearbyActivityDiscovery discovery = service();
        List<Activity> city = discovery.around("Toronto", 25);
        assertFalse(city.isEmpty());
        int requestsAfterAround = requests.get();

        // A small box at the city centre lies fully inside the 1500 m coverage circle, so it
        // is answered from the cached whole-city discovery with no further network traffic.
        List<Activity> covered = discovery.cachedInBounds(
                "Toronto", 43.649, -79.381, 43.651, -79.379);
        assertNotNull(covered);
        assertEquals(city.get(0).getId(), covered.get(0).getId());
        assertEquals(requestsAfterAround, requests.get());

        // A box ~100 km away falls outside coverage; the caller falls back to a real query.
        List<Activity> outside = discovery.cachedInBounds(
                "Toronto", 44.0, -80.0, 44.1, -79.9);
        assertNull(outside);
    }

    @Test
    void concurrentInitialSearchesShareTheCompletedDiscovery() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interpreter", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            requestStarted.countDown();
            try {
                releaseResponse.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = ("{\"elements\":[{\"type\":\"node\",\"id\":9,"
                    + "\"lat\":43.65,\"lon\":-79.38,"
                    + "\"tags\":{\"name\":\"Toronto Cafe\",\"amenity\":\"cafe\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        OverpassNearbyActivityDiscovery discovery = service();
        CompletableFuture<List<Activity>> opening = CompletableFuture.supplyAsync(
                () -> discovery.around("Toronto", 25));
        assertTrue(requestStarted.await(2, TimeUnit.SECONDS));
        CompletableFuture<List<Activity>> firstClick = CompletableFuture.supplyAsync(
                () -> discovery.around("Toronto", 25));
        releaseResponse.countDown();

        assertEquals(1, opening.get(3, TimeUnit.SECONDS).size());
        assertEquals(1, firstClick.get(3, TimeUnit.SECONDS).size());
        assertEquals(1, requests.get());
    }

    private OverpassNearbyActivityDiscovery service() {
        URI endpoint = URI.create("http://127.0.0.1:"
                + server.getAddress().getPort() + "/interpreter");
        return new OverpassNearbyActivityDiscovery(HttpClient.newHttpClient(),
                new ObjectMapper(), destination -> new GeoPoint(43.65, -79.38),
                List.of(endpoint));
    }

    private void start(int status, String response, AtomicReference<String> query) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interpreter", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            query.set(java.net.URLDecoder.decode(body.substring("data=".length()),
                    StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
