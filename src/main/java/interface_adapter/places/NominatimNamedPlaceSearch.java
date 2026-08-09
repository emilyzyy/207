package interface_adapter.places;

import use_case.ports.DestinationGeocoder;
import use_case.ports.NamedPlaceSearch;
import entity.valueobjects.GeoPoint;
import use_case.search.PlaceSearchException;
import use_case.search.SearchFailure;
import entity.entities.Activity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Nominatim is used only for geocoding and user-triggered named-place searches. */
public final class NominatimNamedPlaceSearch implements NamedPlaceSearch, DestinationGeocoder {
    private static final int DEFAULT_DISCOVERY_RADIUS_METERS = 1_500;
    private static final int MAX_DISCOVERY_RADIUS_METERS = 3_000;
    private static final String DEFAULT_ENDPOINT = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT =
            "Trippy-CSC207/1.0 (academic project; github.com/emilyzyy/207)";
    private final HttpClient http;
    private final ObjectMapper json;
    private final URI endpoint;
    private final OsmActivityMapper mapper;
    private final Map<String, List<Activity>> searchCache = new ConcurrentHashMap<>();
    private final Map<String, GeoPoint> geocodeCache = new ConcurrentHashMap<>();
    private long nextRequestAt;

    public NominatimNamedPlaceSearch() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                new ObjectMapper(), URI.create(System.getProperty(
                        "trippy.nominatim.endpoint", DEFAULT_ENDPOINT)));
    }

    NominatimNamedPlaceSearch(HttpClient http, ObjectMapper json, URI endpoint) {
        this.http = http;
        this.json = json;
        this.endpoint = endpoint;
        this.mapper = new OsmActivityMapper(json);
    }

    @Override
    public List<Activity> find(String destination, String query, int limit) {
        if (blank(destination) || blank(query)) return new ArrayList<>();
        String key = normalize(destination) + "|" + normalize(query) + "|" + limit;
        List<Activity> cached = searchCache.get(key);
        if (cached != null) return new ArrayList<>(cached);
        JsonNode results = request(query.trim() + ", " + destination.trim(), limit, true);
        List<Activity> activities = new ArrayList<>();
        for (JsonNode result : results) {
            mapper.fromNominatim(result).ifPresent(activities::add);
        }
        searchCache.put(key, new ArrayList<>(activities));
        return activities;
    }

    @Override
    public GeoPoint geocode(String destination) {
        if (blank(destination)) throw new PlaceSearchException(
                SearchFailure.INVALID_DESTINATION, "A destination is required");
        String key = normalize(destination);
        GeoPoint cached = geocodeCache.get(key);
        if (cached != null) return cached;
        String simplified = simplifyDestination(destination);
        JsonNode results = request(simplified, 1, false);
        if (results.isEmpty() && !simplified.equalsIgnoreCase(destination.trim())) {
            results = request(destination, 1, false);
        }
        if (results.isEmpty()) throw new PlaceSearchException(
                SearchFailure.INVALID_DESTINATION, "Destination was not found: " + destination);
        JsonNode first = results.get(0);
        double latitude = first.path("lat").asDouble();
        double longitude = first.path("lon").asDouble();
        GeoPoint point = new GeoPoint(latitude, longitude,
                discoveryRadius(first.path("boundingbox"), latitude));
        geocodeCache.put(key, point);
        return point;
    }

    private JsonNode request(String query, int limit, boolean details) {
        String parameters = "?q=" + encode(query) + "&format=jsonv2&limit=" + limit
                + (details ? "&addressdetails=1&extratags=1&namedetails=1" : "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + parameters))
                .timeout(Duration.ofSeconds(12)).header("User-Agent", USER_AGENT).GET().build();
        try {
            throttle();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429) throw new PlaceSearchException(
                    SearchFailure.RATE_LIMITED, "OpenStreetMap search is rate limited");
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                        "Nominatim HTTP " + response.statusCode());
            JsonNode tree = json.readTree(response.body());
            if (!tree.isArray()) throw new IOException("Invalid Nominatim response");
            return tree;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                    "Place search was interrupted", exception);
        } catch (IOException exception) {
            throw new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                    "Named-place search is unavailable", exception);
        }
    }

    private synchronized void throttle() throws InterruptedException {
        // Public Nominatim permits at most one request per second per application.
        long wait = nextRequestAt - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        nextRequestAt = System.currentTimeMillis() + 1000L;
    }

    private static String normalize(String value) { return value.trim().toLowerCase(); }

    /** Removes duplicated regional wording emitted by some place pickers. */
    static String simplifyDestination(String destination) {
        if (destination == null) return "";
        Map<String, String> unique = new LinkedHashMap<>();
        for (String rawPart : destination.split(",")) {
            String part = rawPart.trim().replaceFirst("(?i)\\s+island$", "").trim();
            if (!part.isEmpty()) unique.putIfAbsent(normalize(part), part);
        }
        return String.join(", ", unique.values());
    }

    private static int discoveryRadius(JsonNode bounds, double latitude) {
        if (!bounds.isArray() || bounds.size() < 4) return DEFAULT_DISCOVERY_RADIUS_METERS;
        double northSouth = Math.abs(bounds.get(1).asDouble() - bounds.get(0).asDouble())
                * 111_320.0;
        double eastWest = Math.abs(bounds.get(3).asDouble() - bounds.get(2).asDouble())
                * 111_320.0 * Math.cos(Math.toRadians(latitude));
        double smallerSpan = Math.min(northSouth, eastWest);
        if (!Double.isFinite(smallerSpan) || smallerSpan <= 0) {
            return DEFAULT_DISCOVERY_RADIUS_METERS;
        }
        return Math.min(MAX_DISCOVERY_RADIUS_METERS,
                Math.max(DEFAULT_DISCOVERY_RADIUS_METERS,
                        (int) Math.round(smallerSpan / 2.0)));
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
