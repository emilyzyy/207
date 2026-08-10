package interface_adapter.places;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import use_case.ports.CityCandidate;

final class OpenMeteoCitySearchTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesGeocodedCandidates() throws IOException {
        final AtomicInteger requests = new AtomicInteger();
        start(200, "{\"results\":[{\"name\":\"London\",\"admin1\":\"Ontario\","
                + "\"country\":\"Canada\",\"latitude\":42.98,\"longitude\":-81.25},"
                + "{\"name\":\"London\",\"country\":\"England\","
                + "\"latitude\":51.51,\"longitude\":-0.13}]}", requests);

        final List<CityCandidate> candidates = service().search("lon", 8);

        assertEquals(2, candidates.size());
        assertEquals(1, requests.get());
        final CityCandidate first = candidates.get(0);
        assertEquals("London", first.getName());
        assertEquals("Ontario", first.getRegion());
        assertEquals("Canada", first.getCountry());
        assertEquals(42.98, first.getLatitude());
        assertEquals(-81.25, first.getLongitude());
        assertEquals(51.51, candidates.get(1).getLatitude());
    }

    @Test
    void blankQueryDoesNotCallTheServer() throws IOException {
        final AtomicInteger requests = new AtomicInteger();
        start(200, "{\"results\":[]}", requests);

        final List<CityCandidate> candidates = service().search("   ", 8);

        assertTrue(candidates.isEmpty());
        assertEquals(0, requests.get());
    }

    @Test
    void nonSuccessResponseReturnsNoCandidates() throws IOException {
        final AtomicInteger requests = new AtomicInteger();
        start(500, "boom", requests);

        final List<CityCandidate> candidates = service().search("Toronto", 8);

        assertTrue(candidates.isEmpty());
        assertEquals(1, requests.get());
    }

    private OpenMeteoCitySearch service() {
        return new OpenMeteoCitySearch(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"));
    }

    private void start(int status, String body, AtomicInteger requests) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            requests.incrementAndGet();
            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
