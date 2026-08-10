package interface_adapter.routing;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import use_case.ports.DistanceService;

public final class OsrmDistanceService implements DistanceService {
    private static final String OSRM_BASE = "https://routing.openstreetmap.de";
    private static final String TRANSITOUS_BASE = "https://api.transitous.org";
    private static final String TOMTOM_BASE = "https://api.tomtom.com/routing/1/calculateRoute";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Supplier<String> apiKey;
    private static boolean warned = false;

    public OsrmDistanceService() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), new ObjectMapper(),
                OsrmDistanceService::configuredKey);
    }

    /**
     * Package-visible constructor used by tests.
     * @param mapper the m ap pe r value
     * @param client the c li en t value
     */
    OsrmDistanceService(HttpClient client, ObjectMapper mapper) {
        this(client, mapper, OsrmDistanceService::configuredKey);
    }

    OsrmDistanceService(HttpClient client, ObjectMapper mapper, Supplier<String> apiKey) {
        this.client = client;
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    /**
     * The TomTom key is a configuration concern owned by the composition root, which passes
     * it in; the adapter never reads {@code .env} or system properties itself.
      * @param apiKey the a pi ke y value
     */
    public OsrmDistanceService(Supplier<String> apiKey) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), new ObjectMapper(), apiKey);
    }

    @Override
    public int estimateTravelMinutes(Location from, Location to, TransportationMode mode, LocalDateTime departure) {
        switch (mode) {
            case WALKING:
                return estimateOsrm(from, to, "routed-foot", "foot");
            case DRIVING:
                return estimateDriving(from, to, departure);
            case TRANSIT:
                return estimateTransit(from, to, departure);
            default:
                return estimateDriving(from, to, departure);
        }
    }

    private int estimateDriving(Location from, Location to, LocalDateTime departure) {
        final String key = apiKey.get();
        if (key == null || key.isBlank()) {
            warnOnce("No TomTom API key set (tomtom.api.key or TOMTOM_API_KEY); "
                    + "driving estimates fall back to OSRM without traffic awareness");
            return estimateOsrm(from, to, "routed-car", "car");
        }
        final Integer minutes = estimateTomtom(from, to, departure, key);
        if (minutes != null) {
            return minutes;
        }
        warnOnce("TomTom route request failed; driving estimates fall back to OSRM");
        return estimateOsrm(from, to, "routed-car", "car");
    }

    private Integer estimateTomtom(Location from, Location to, LocalDateTime departure, String key) {
        final double lng1 = from.getLongitude();
        final double lat1 = from.getLatitude();
        final double lng2 = to.getLongitude();
        final double lat2 = to.getLatitude();
        final String departAt = departure.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        // TomTom orders coordinates latitude,longitude, unlike the OSRM calls below.
        // Kept from the parallel fix on feature/emily-autoschedule: the implementation here
        // is Raashid's, and this line records why the order is what it is so the defect is
        // not reintroduced a third time.
        final String url = TOMTOM_BASE + "/" + lat1 + "," + lng1 + ":" + lat2 + "," + lng2 + "/json"
                + "?key=" + urlEncode(key)
                + "&departAt=" + urlEncode(departAt)
                + "&traffic=true"
                + "&computeTravelTimeFor=all";
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "Trippy-CSC207/1.0")
                    .GET().build();
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                warnOnce("TomTom route request failed (" + response.statusCode() + "): "
                        + tomtomError(response.body())
                        + "; driving estimates fall back to OSRM");
                return null;
            }
            final JsonNode root = mapper.readTree(response.body());
            final JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                return null;
            }
            final double seconds = routes.get(0).path("summary").path("travelTimeInSeconds").asDouble(-1);
            if (seconds <= 0) {
                return null;
            }
            return Math.max(1, (int) Math.round(seconds / 60.0));
        }
        catch (Exception e) {
            warnOnce("TomTom route request failed: " + e + "; driving estimates fall back to OSRM");
            return null;
        }
    }

    private String tomtomError(String body) {
        if (body == null || body.isBlank()) {
            return "no error body";
        }
        try {
            final JsonNode detail = mapper.readTree(body).path("detailedError");
            final String code = detail.path("code").asText("");
            final String message = detail.path("message").asText("");
            if (!code.isEmpty() || !message.isEmpty()) {
                return (code.isEmpty() ? "" : code + ": ")
                        + (message.isEmpty() ? "no message" : message);
            }
            return body;
        }
        catch (Exception e) {
            return body;
        }
    }

    private int estimateTransit(Location from, Location to, LocalDateTime departure) {
        final double lat1 = from.getLatitude();
        final double lng1 = from.getLongitude();
        final double lat2 = to.getLatitude();
        final double lng2 = to.getLongitude();
        final ZonedDateTime zoned = departure.atZone(ZoneId.systemDefault());
        final String time = zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        final String url = TRANSITOUS_BASE + "/api/v6/plan"
                + "?fromPlace=" + urlEncode(lat1 + "," + lng1)
                + "&toPlace=" + urlEncode(lat2 + "," + lng2)
                + "&time=" + urlEncode(time)
                + "&arriveBy=false"
                + "&numItineraries=1";
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "Trippy-CSC207/1.0")
                    .GET().build();
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return fallback(from, to, from.calculateDistanceTo(to), TransportationMode.TRANSIT);
            }
            final JsonNode root = mapper.readTree(response.body());
            final JsonNode itineraries = root.path("itineraries");
            if (!itineraries.isArray() || itineraries.isEmpty()) {
                return fallback(from, to, from.calculateDistanceTo(to), TransportationMode.TRANSIT);
            }
            final double seconds = itineraries.get(0).path("duration").asDouble(-1);
            if (seconds <= 0) {
                return fallback(from, to, from.calculateDistanceTo(to), TransportationMode.TRANSIT);
            }
            return Math.max(1, (int) Math.round(seconds / 60.0));
        }
        catch (Exception e) {
            return fallback(from, to, from.calculateDistanceTo(to), TransportationMode.TRANSIT);
        }
    }

    private int estimateOsrm(Location from, Location to, String server, String profile) {
        final double lng1 = from.getLongitude();
        final double lat1 = from.getLatitude();
        final double lng2 = to.getLongitude();
        final double lat2 = to.getLatitude();
        final String url = OSRM_BASE + "/" + server + "/route/v1/" + profile + "/"
                + lng1 + "," + lat1 + ";" + lng2 + "," + lat2
                + "?overview=false";
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "Trippy-CSC207/1.0")
                    .GET().build();
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
            }
            final JsonNode root = mapper.readTree(response.body());
            if (!"Ok".equals(root.path("code").asText())) {
                return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
            }
            final JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
            }
            final double seconds = routes.get(0).path("duration").asDouble(-1);
            if (seconds <= 0) {
                return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
            }
            return Math.max(1, (int) Math.round(seconds / 60.0));
        }
        catch (Exception e) {
            return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
        }
    }

    /**
     * Reads only the JVM property / environment variable; the composition root owns {@code .env}.
     * @return the result of the operation
     */
    private static String configuredKey() {
        final String fromProperty = System.getProperty("tomtom.api.key");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        final String fromEnvironment = System.getenv("TOMTOM_API_KEY");
        return fromEnvironment != null && !fromEnvironment.isBlank() ? fromEnvironment.trim() : null;
    }

    private static void warnOnce(String message) {
        if (!warned) {
            warned = true;
            System.err.println("[OsrmDistance] " + message);
        }
    }

    private static TransportationMode modeFromProfile(String profile) {
        return "car".equals(profile) ? TransportationMode.DRIVING : TransportationMode.WALKING;
    }

    private static int fallback(Location from, Location to, double haversineKm, TransportationMode mode) {
        final double km = Math.max(0.5, haversineKm);
        final double speed = mode == TransportationMode.DRIVING ? 24.0
                : mode == TransportationMode.TRANSIT ? 16.0 : 4.8;
        return Math.max(10, (int) Math.round(km / speed * 60.0 + (mode == TransportationMode.TRANSIT ? 6 : 2)));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
