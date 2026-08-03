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
import java.time.Duration;

public final class OsrmDistanceService implements DistanceService {
    private static final String OSRM_BASE = "https://routing.openstreetmap.de";
    private static final String TRANSITOUS_BASE = "https://api.transitous.org";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;
    private final ObjectMapper mapper;

    public OsrmDistanceService() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), new ObjectMapper());
    }

    OsrmDistanceService(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public int estimateTravelMinutes(Location from, Location to, TransportationMode mode) {
        switch (mode) {
            case WALKING:
                return estimateOsrm(from, to, "routed-foot", "foot");
            case DRIVING:
                return estimateOsrm(from, to, "routed-car", "car");
            case TRANSIT:
                return estimateTransit(from, to);
            default:
                return estimateOsrm(from, to, "routed-car", "car");
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

    private int estimateTransit(Location from, Location to) {
        double lat1 = from.getLatitude();
        double lng1 = from.getLongitude();
        double lat2 = to.getLatitude();
        double lng2 = to.getLongitude();
        String url = TRANSITOUS_BASE + "/api/v6/plan"
                + "?fromPlace=" + urlEncode(lat1 + "," + lng1)
                + "&toPlace=" + urlEncode(lat2 + "," + lng2)
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
