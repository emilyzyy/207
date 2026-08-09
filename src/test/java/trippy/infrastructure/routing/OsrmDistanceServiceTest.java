package trippy.infrastructure.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for the routing adapter, driven through its injectable HttpClient.
 *
 * <p>These exist because the request URL is the part of an HTTP adapter that unit tests
 * usually never see, and it is where a silent defect can hide: a wrongly ordered pair of
 * coordinates still produces a well-formed request, still parses, and still falls back
 * quietly, so everything downstream looks healthy while the provider is never really
 * being used.</p>
 */
class OsrmDistanceServiceTest {

    /** Toronto: latitude ~43.6, longitude ~-79.4. The two are impossible to confuse here. */
    private static final Location UNION_STATION = new Location(43.6453, -79.3806, "Union");
    private static final Location CASA_LOMA = new Location(43.6780, -79.4094, "Casa Loma");
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 8, 12, 17, 30);

    private final RecordingHttpClient client = new RecordingHttpClient();

    private OsrmDistanceService serviceWith(String... bodies) {
        for (String body : bodies) {
            client.enqueue(200, body);
        }
        return new OsrmDistanceService(client, new ObjectMapper());
    }

    private OsrmDistanceService serviceWithoutTomtomKey(String... bodies) {
        for (String body : bodies) {
            client.enqueue(200, body);
        }
        return new OsrmDistanceService(client, new ObjectMapper(), () -> null);
    }

    private static String tomtomBody(int seconds) {
        return "{\"routes\":[{\"summary\":{\"travelTimeInSeconds\":" + seconds + "}}]}";
    }

    private static String osrmBody(int seconds) {
        return "{\"code\":\"Ok\",\"routes\":[{\"duration\":" + seconds + "}]}";
    }

    private static String transitousBody(int seconds) {
        return "{\"itineraries\":[{\"duration\":" + seconds + "}]}";
    }

    @Test
    void tomtomReceivesCoordinatesAsLatitudeThenLongitude() {
        withTomtomKey(() -> {
            OsrmDistanceService service = serviceWith(tomtomBody(900));

            service.estimateTravelMinutes(UNION_STATION, CASA_LOMA,
                    TransportationMode.DRIVING, DEPARTURE);

            String url = client.lastUri().toString();
            assertTrue(url.contains("43.6453,-79.3806:43.678,-79.4094"),
                    "TomTom expects latitude,longitude; request was " + url);
            assertFalse(url.contains("-79.3806,43.6453"),
                    "longitude must not come first for TomTom: " + url);
        });
    }

    @Test
    void tomtomRequestCarriesTheProposedDepartureTime() {
        withTomtomKey(() -> {
            OsrmDistanceService service = serviceWith(tomtomBody(900));

            service.estimateTravelMinutes(UNION_STATION, CASA_LOMA,
                    TransportationMode.DRIVING, DEPARTURE);

            String url = client.lastUri().toString();
            assertTrue(url.contains("departAt=2026-08-12T17%3A30%3A00")
                            || url.contains("departAt=2026-08-12T17:30:00"),
                    "the departure time should reach TomTom: " + url);
            assertTrue(url.contains("traffic=true"));
        });
    }

    @Test
    void tomtomTravelTimeIsParsedIntoMinutes() {
        withTomtomKey(() -> {
            OsrmDistanceService service = serviceWith(tomtomBody(1500));

            int minutes = service.estimateTravelMinutes(UNION_STATION, CASA_LOMA,
                    TransportationMode.DRIVING, DEPARTURE);

            assertEquals(25, minutes);
            assertEquals(1, client.requestCount(), "a successful TomTom call needs no fallback");
        });
    }

    @Test
    void aFailedTomtomCallFallsBackToOsrmDriving() {
        withTomtomKey(() -> {
            client.enqueue(500, "");
            client.enqueue(200, osrmBody(1200));
            OsrmDistanceService service = new OsrmDistanceService(client, new ObjectMapper());

            int minutes = service.estimateTravelMinutes(UNION_STATION, CASA_LOMA,
                    TransportationMode.DRIVING, DEPARTURE);

            assertEquals(20, minutes);
            assertEquals(2, client.requestCount());
            assertTrue(client.lastUri().toString().contains("routed-car"),
                    "the second attempt should be the OSRM driving profile");
        });
    }

    @Test
    void drivingUsesOsrmDirectlyWhenNoKeyIsConfigured() {
        OsrmDistanceService service = serviceWithoutTomtomKey(osrmBody(600));

        int minutes = service.estimateTravelMinutes(UNION_STATION, CASA_LOMA,
                TransportationMode.DRIVING, DEPARTURE);

        assertEquals(10, minutes);
        assertEquals(1, client.requestCount());
        assertTrue(client.lastUri().toString().contains("routed-car"));
    }

    @Test
    void osrmReceivesCoordinatesAsLongitudeThenLatitude() {
        OsrmDistanceService service = serviceWith(osrmBody(600));

        service.estimateTravelMinutes(UNION_STATION, CASA_LOMA,
                TransportationMode.WALKING, DEPARTURE);

        String url = client.lastUri().toString();
        assertTrue(url.contains("-79.3806,43.6453;-79.4094,43.678"),
                "OSRM expects longitude,latitude; request was " + url);
        assertTrue(url.contains("routed-foot"));
    }

    @Test
    void transitReceivesLatitudeLongitudeAndADepartureTime() {
        OsrmDistanceService service = serviceWith(transitousBody(1800));

        int minutes = service.estimateTravelMinutes(UNION_STATION, CASA_LOMA,
                TransportationMode.TRANSIT, DEPARTURE);

        assertEquals(30, minutes);
        String url = client.lastUri().toString();
        assertTrue(url.contains("fromPlace=43.6453%2C-79.3806"), "request was " + url);
        assertTrue(url.contains("time="), "transit should be timetable-aware: " + url);
        assertTrue(url.contains("arriveBy=false"));
    }

    @Test
    void anUnusableResponseStillYieldsAPositiveEstimate() {
        OsrmDistanceService service = serviceWith("{\"code\":\"NoRoute\"}");

        int minutes = service.estimateTravelMinutes(UNION_STATION, CASA_LOMA,
                TransportationMode.WALKING, DEPARTURE);

        assertTrue(minutes > 0, "the existing distance-based fallback should still apply");
    }

    @Test
    void noApiKeyIsHardCodedInTheAdapter() {
        // The key must come from configuration; a literal here would be committed to Git.
        OsrmDistanceService service = serviceWithoutTomtomKey(osrmBody(600));
        service.estimateTravelMinutes(UNION_STATION, CASA_LOMA,
                TransportationMode.DRIVING, DEPARTURE);
        assertFalse(client.lastUri().toString().contains("key="),
                "without configuration there is no key to send");
    }

    private void withTomtomKey(Runnable body) {
        String previous = System.getProperty("tomtom.api.key");
        System.setProperty("tomtom.api.key", "test-key-not-a-real-credential");
        try {
            body.run();
        } finally {
            if (previous == null) {
                System.clearProperty("tomtom.api.key");
            } else {
                System.setProperty("tomtom.api.key", previous);
            }
        }
    }

    /** Serves queued responses and remembers what was asked for. */
    private static final class RecordingHttpClient extends HttpClient {
        private final List<URI> requested = new ArrayList<>();
        private final List<int[]> statuses = new ArrayList<>();
        private final List<String> bodies = new ArrayList<>();
        private int served;

        void enqueue(int status, String body) {
            statuses.add(new int[] {status});
            bodies.add(body);
        }

        URI lastUri() {
            return requested.get(requested.size() - 1);
        }

        int requestCount() {
            return requested.size();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request,
                                        HttpResponse.BodyHandler<T> responseBodyHandler) {
            requested.add(request.uri());
            int index = Math.min(served, bodies.size() - 1);
            served++;
            return (HttpResponse<T>) new StubResponse(request.uri(),
                    statuses.get(index)[0], bodies.get(index));
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<java.net.CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<java.net.ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public javax.net.ssl.SSLContext sslContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<java.net.Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<java.util.concurrent.Executor> executor() {
            return Optional.empty();
        }
    }

    /** Minimal HttpResponse carrying a status and a body. */
    private static final class StubResponse implements HttpResponse<String> {
        private final URI uri;
        private final int status;
        private final String body;

        StubResponse(URI uri, int status, String body) {
            this.uri = uri;
            this.status = status;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(uri).build();
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            BiPredicate<String, String> keepAll = (name, value) -> true;
            return HttpHeaders.of(new java.util.HashMap<>(), keepAll);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    /** Present so an unused-import warning cannot mask a real IO failure path. */
    @SuppressWarnings("unused")
    private static void ioSignature() throws IOException {
        throw new IOException();
    }
}
