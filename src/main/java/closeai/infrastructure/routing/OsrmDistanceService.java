package closeai.infrastructure.routing;

import closeai.application.ports.DistanceService;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class OsrmDistanceService implements DistanceService {
    private static final String OSRM_BASE = "https://routing.openstreetmap.de";
    private static final String TRANSITOUS_BASE = "https://api.transitous.org";
    private static final String TOMTOM_BASE = "https://api.tomtom.com/routing/1/calculateRoute";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;
    private final ObjectMapper mapper;
    private static boolean warned = false;

    public OsrmDistanceService() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), new ObjectMapper());
    }

    OsrmDistanceService(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
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
        String key = tomtomApiKey();
        if (key == null || key.isBlank()) {
            warnOnce("No TomTom API key set (tomtom.api.key or TOMTOM_API_KEY); "
                    + "driving estimates fall back to OSRM without traffic awareness");
            return estimateOsrm(from, to, "routed-car", "car");
        }
        Integer minutes = estimateTomtom(from, to, departure, key);
        if (minutes != null) return minutes;
        warnOnce("TomTom route request failed; driving estimates fall back to OSRM");
        return estimateOsrm(from, to, "routed-car", "car");
    }

    private Integer estimateTomtom(Location from, Location to, LocalDateTime departure, String key) {
        double lng1 = from.getLongitude();
        double lat1 = from.getLatitude();
        double lng2 = to.getLongitude();
        double lat2 = to.getLatitude();
        String departAt = departure.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        // TomTom orders coordinates latitude,longitude, unlike the OSRM calls below.
        // Kept from the parallel fix on feature/emily-autoschedule: the implementation here
        // is Raashid's, and this line records why the order is what it is so the defect is
        // not reintroduced a third time.
        String url = TOMTOM_BASE + "/" + lat1 + "," + lng1 + ":" + lat2 + "," + lng2 + "/json"
                + "?key=" + urlEncode(key)
                + "&departAt=" + urlEncode(departAt)
                + "&traffic=true"
                + "&computeTravelTimeFor=all";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "CloseAI-CSC207/1.0")
                    .GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                warnOnce("TomTom route request failed (" + response.statusCode() + "): "
                        + tomtomError(response.body())
                        + "; driving estimates fall back to OSRM");
                return null;
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                return null;
            }
            double seconds = routes.get(0).path("summary").path("travelTimeInSeconds").asDouble(-1);
            if (seconds <= 0) {
                return null;
            }
            return Math.max(1, (int) Math.round(seconds / 60.0));
        } catch (Exception e) {
            warnOnce("TomTom route request failed: " + e + "; driving estimates fall back to OSRM");
            return null;
        }
    }

    private String tomtomError(String body) {
        if (body == null || body.isBlank()) {
            return "no error body";
        }
        try {
            JsonNode detail = mapper.readTree(body).path("detailedError");
            String code = detail.path("code").asText("");
            String message = detail.path("message").asText("");
            if (!code.isEmpty() || !message.isEmpty()) {
                return (code.isEmpty() ? "" : code + ": ")
                        + (message.isEmpty() ? "no message" : message);
            }
            return body;
        } catch (Exception e) {
            return body;
        }
    }

    private int estimateTransit(Location from, Location to, LocalDateTime departure) {
        double lat1 = from.getLatitude();
        double lng1 = from.getLongitude();
        double lat2 = to.getLatitude();
        double lng2 = to.getLongitude();
        ZonedDateTime zoned = departure.atZone(ZoneId.systemDefault());
        String time = zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String url = TRANSITOUS_BASE + "/api/v6/plan"
                + "?fromPlace=" + urlEncode(lat1 + "," + lng1)
                + "&toPlace=" + urlEncode(lat2 + "," + lng2)
                + "&time=" + urlEncode(time)
                + "&arriveBy=false"
                + "&numItineraries=1";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "CloseAI-CSC207/1.0")
                    .GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return fallback(from, to, from.calculateDistanceTo(to), TransportationMode.TRANSIT);
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode itineraries = root.path("itineraries");
            if (!itineraries.isArray() || itineraries.isEmpty()) {
                return fallback(from, to, from.calculateDistanceTo(to), TransportationMode.TRANSIT);
            }
            double seconds = itineraries.get(0).path("duration").asDouble(-1);
            if (seconds <= 0) {
                return fallback(from, to, from.calculateDistanceTo(to), TransportationMode.TRANSIT);
            }
            return Math.max(1, (int) Math.round(seconds / 60.0));
        } catch (Exception e) {
            return fallback(from, to, from.calculateDistanceTo(to), TransportationMode.TRANSIT);
        }
    }

    private int estimateOsrm(Location from, Location to, String server, String profile) {
        double lng1 = from.getLongitude();
        double lat1 = from.getLatitude();
        double lng2 = to.getLongitude();
        double lat2 = to.getLatitude();
        String url = OSRM_BASE + "/" + server + "/route/v1/" + profile + "/"
                + lng1 + "," + lat1 + ";" + lng2 + "," + lat2
                + "?overview=false";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "CloseAI-CSC207/1.0")
                    .GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
            }
            JsonNode root = mapper.readTree(response.body());
            if (!"Ok".equals(root.path("code").asText())) {
                return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
            }
            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
            }
            double seconds = routes.get(0).path("duration").asDouble(-1);
            if (seconds <= 0) {
                return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
            }
            return Math.max(1, (int) Math.round(seconds / 60.0));
        } catch (Exception e) {
            return fallback(from, to, from.calculateDistanceTo(to), modeFromProfile(profile));
        }
    }

    private static String tomtomApiKey() {
        String key = System.getProperty("tomtom.api.key");
        if (key != null && !key.isBlank()) {
            return key;
        }
        key = System.getenv("TOMTOM_API_KEY");
        if (key != null && !key.isBlank()) {
            return key;
        }
        return keyFromDotEnv();
    }

    private static String dotEnvKey = null;
    private static boolean dotEnvLoaded = false;

    private static String keyFromDotEnv() {
        if (dotEnvLoaded) {
            return dotEnvKey;
        }
        dotEnvLoaded = true;
        try {
            Path dotEnv = Paths.get(System.getProperty("user.dir"), ".env");
            if (!Files.isReadable(dotEnv)) {
                return dotEnvKey;
            }
            for (String line : Files.readAllLines(dotEnv, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = trimmed.substring(0, eq).trim();
                if ("TOMTOM_API_KEY".equals(name)) {
                    dotEnvKey = trimmed.substring(eq + 1).trim();
                    return dotEnvKey;
                }
            }
        } catch (Exception ignored) {
            dotEnvKey = null;
        }
        return dotEnvKey;
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
        double km = Math.max(0.5, haversineKm);
        double speed = mode == TransportationMode.DRIVING ? 24.0
                : mode == TransportationMode.TRANSIT ? 16.0 : 4.8;
        return Math.max(10, (int) Math.round(km / speed * 60.0 + (mode == TransportationMode.TRANSIT ? 6 : 2)));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
