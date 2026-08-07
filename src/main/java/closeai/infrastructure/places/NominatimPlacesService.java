package closeai.infrastructure.places;

import closeai.application.ports.PlacesService;
import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** PlacesService adapter backed by OpenStreetMap Nominatim (geocoding) and Overpass (POI search). */
public final class NominatimPlacesService implements PlacesService {
    private static final URI GEOCODING_ENDPOINT =
            URI.create("https://nominatim.openstreetmap.org/search");
    private static final List<URI> OVERPASS_ENDPOINTS = List.of(
            URI.create("https://overpass-api.de/api/interpreter"),
            URI.create("https://overpass.kumi.systems/api/interpreter"),
            URI.create("https://overpass.private.coffee/api/interpreter"),
            URI.create("https://overpass.osm.jp/api/interpreter"));
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration OVERPASS_TIMEOUT = Duration.ofSeconds(20);
    private static final double SEARCH_RADIUS_METERS = 1500;
    private static final int MAX_RESULTS = 25;
    private static final int MAX_BUSY_RETRIES = 3;
    private static final long[] BUSY_RETRY_DELAY_MILLIS =
            {300L, 800L, 2000L};

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI geocodingEndpoint;
    private final URI overpassEndpoint;
    private final boolean tryFallbacks;
    private final Map<String, List<Activity>> cache = new ConcurrentHashMap<>();
    private final Map<String, List<Activity>> boundsCache = new ConcurrentHashMap<>();

    public NominatimPlacesService() {
        this(
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
                new ObjectMapper(),
                GEOCODING_ENDPOINT,
                OVERPASS_ENDPOINTS.get(0),
                true);
    }

    NominatimPlacesService(
            HttpClient client,
            ObjectMapper mapper,
            URI geocodingEndpoint,
            URI overpassEndpoint) {
        this(client, mapper, geocodingEndpoint, overpassEndpoint, false);
    }

    private NominatimPlacesService(
            HttpClient client,
            ObjectMapper mapper,
            URI geocodingEndpoint,
            URI overpassEndpoint,
            boolean tryFallbacks) {
        if (client == null || mapper == null
                || geocodingEndpoint == null || overpassEndpoint == null) {
            throw new IllegalArgumentException("Places service dependencies are required");
        }
        this.client = client;
        this.mapper = mapper;
        this.geocodingEndpoint = geocodingEndpoint;
        this.overpassEndpoint = overpassEndpoint;
        this.tryFallbacks = tryFallbacks;
    }

    @Override
    public List<Activity> search(String destination, String query) {
        if (destination == null || destination.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String key = destination.trim().toLowerCase();
        List<Activity> cached = cache.get(key);
        if (cached == null) {
            cached = searchUncached(destination);
            if (!cached.isEmpty()) {
                cache.put(key, cached);
            }
        }
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.isEmpty()) {
            return new ArrayList<>(cached);
        }
        List<Activity> result = new ArrayList<>();
        for (Activity activity : cached) {
            if (activity.getName().toLowerCase().contains(needle)
                    || activity.getCategory().name().toLowerCase().contains(needle)
                    || activity.getLocation().getAddress().toLowerCase().contains(needle)) {
                result.add(activity);
            }
        }
        return result;
    }

    @Override
    public List<Activity> searchInBounds(double south, double west, double north, double east,
                                         int maxResults) {
        String key = quantize(south, west, north, east);
        List<Activity> cached = boundsCache.get(key);
        if (cached != null) {
            return cached;
        }
        String overpassQuery = buildBoundingBoxQuery(south, west, north, east, maxResults);
        JsonNode elements = queryOverpass(overpassQuery);
        List<Activity> result = parseElements(elements, maxResults);
        if (!result.isEmpty()) {
            boundsCache.put(key, result);
        }
        return result;
    }

    /** Groups nearby viewport windows onto a shared key so panning reuses one query result. */
    private static String quantize(double south, double west, double north, double east) {
        long s = Math.round(south * 100);
        long w = Math.round(west * 100);
        long n = Math.round(north * 100);
        long e = Math.round(east * 100);
        return s + "," + w + "," + n + "," + e;
    }

    private List<Activity> searchUncached(String destination) {
        try {
            double[] coords = geocode(destination);
            String overpassQuery = buildOverpassQuery(coords[0], coords[1]);
            System.out.println("[NominatimPlaces] Querying Overpass for " + destination + "...");
            JsonNode elements = queryOverpass(overpassQuery);
            System.out.println("[NominatimPlaces] Got " + elements.size() + " elements");
            List<Activity> result = parseElements(elements, MAX_RESULTS);
            System.out.println("[NominatimPlaces] Parsed " + result.size() + " activities");
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("[NominatimPlaces] Search interrupted");
            return new ArrayList<>();
        } catch (IOException | RuntimeException e) {
            System.err.println("[NominatimPlaces] Search failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private double[] geocode(String destination) throws IOException, InterruptedException {
        URI uri = URI.create(geocodingEndpoint.toString()
                + "?q=" + encode(destination) + "&format=json&limit=1");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "CloseAI-CSC207/1.0")
                .GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Nominatim HTTP " + response.statusCode());
        }
        JsonNode results = mapper.readTree(response.body());
        if (!results.isArray() || results.isEmpty()) {
            throw new IOException("Nominatim found no location for: " + destination);
        }
        JsonNode first = results.get(0);
        return new double[]{first.get("lat").asDouble(), first.get("lon").asDouble()};
    }

    private String buildOverpassQuery(double lat, double lon) {
        int r = (int) SEARCH_RADIUS_METERS;
        return "[out:json][timeout:30];"
            + "("
            + "node[\"amenity\"=\"restaurant\"](around:" + r + "," + lat + "," + lon + ");"
            + "node[\"amenity\"=\"cafe\"](around:" + r + "," + lat + "," + lon + ");"
            + "node[\"tourism\"=\"museum\"](around:" + r + "," + lat + "," + lon + ");"
            + "node[\"tourism\"=\"attraction\"](around:" + r + "," + lat + "," + lon + ");"
            + "node[\"shop\"](around:" + r + "," + lat + "," + lon + ");"
            + ");"
            + "out body " + MAX_RESULTS + ";";
    }

    private String buildBoundingBoxQuery(double south, double west, double north, double east,
                                         int maxResults) {
        return "[out:json][timeout:30];"
            + "("
            + "node[\"amenity\"=\"restaurant\"](bbox:" + south + "," + west + "," + north + "," + east + ");"
            + "node[\"amenity\"=\"cafe\"](bbox:" + south + "," + west + "," + north + "," + east + ");"
            + "node[\"tourism\"=\"museum\"](bbox:" + south + "," + west + "," + north + "," + east + ");"
            + "node[\"tourism\"=\"attraction\"](bbox:" + south + "," + west + "," + north + "," + east + ");"
            + "node[\"shop\"](bbox:" + south + "," + west + "," + north + "," + east + ");"
            + ");"
            + "out body " + maxResults + ";";
    }

    private JsonNode queryOverpass(String query) {
        int busyRetries = 0;
        while (true) {
            boolean sawBusy = false;
            for (URI endpoint : overpassCandidates()) {
                try {
                    return queryEndpoint(endpoint, query);
                } catch (OverpassBusyException busy) {
                    sawBusy = true;
                    System.err.println("[NominatimPlaces] Overpass " + endpoint.getHost()
                            + " unavailable (server busy), will retry");
                } catch (Exception e) {
                    System.err.println("[NominatimPlaces] Overpass " + endpoint.getHost()
                            + " failed: " + e.getMessage());
                }
            }
            if (!sawBusy || busyRetries >= MAX_BUSY_RETRIES) {
                return mapper.createArrayNode();
            }
            long delay = BUSY_RETRY_DELAY_MILLIS[Math.min(busyRetries,
                    BUSY_RETRY_DELAY_MILLIS.length - 1)];
            busyRetries++;
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return mapper.createArrayNode();
            }
        }
    }

    private List<URI> overpassCandidates() {
        List<URI> candidates = new ArrayList<>();
        candidates.add(overpassEndpoint);
        if (tryFallbacks) {
            for (URI endpoint : OVERPASS_ENDPOINTS) {
                if (!candidates.contains(endpoint)) {
                    candidates.add(endpoint);
                }
            }
        }
        return candidates;
    }

    private JsonNode queryEndpoint(URI endpoint, String query)
            throws IOException, InterruptedException {
        String body = "data=" + encode(query);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(OVERPASS_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "CloseAI-CSC207/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Overpass HTTP " + response.statusCode());
        }
        String text = response.body();
        String trimmed = text == null ? "" : text.strip();
        if (!trimmed.isEmpty() && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new OverpassBusyException("Overpass returned a non-JSON (overloaded) page");
        }
        JsonNode tree = mapper.readTree(trimmed);
        return tree.has("elements") ? tree.get("elements") : mapper.createArrayNode();
    }

    private List<Activity> parseElements(JsonNode elements, int limit) {
        List<Activity> activities = new ArrayList<>();
        if (elements == null || !elements.isArray()) return activities;
        int idx = 0;
        for (JsonNode el : elements) {
            if (idx >= limit) break;
            double lat = el.has("lat") ? el.get("lat").asDouble() : 0;
            double lon = el.has("lon") ? el.get("lon").asDouble() : 0;
            if (!Double.isFinite(lat) || !Double.isFinite(lon)) continue;
            JsonNode tags = el.has("tags") ? el.get("tags") : mapper.createObjectNode();
            String name = englishName(tags);
            if (name == null || name.trim().isEmpty()) continue;
            String amenity = tags.has("amenity") ? tags.get("amenity").asText() : "";
            String tourism = tags.has("tourism") ? tags.get("tourism").asText() : "";
            String shop = tags.has("shop") ? tags.get("shop").asText() : "";
            ActivityCategory category = categorize(amenity, tourism, shop);
            IndoorOutdoorType ioType = inferIndoorOutdoor(category);
            String address = buildAddress(tags);
            String id = "osm-" + el.get("id").asLong();
            int duration = estimateDuration(category);
            String hoursText = tags.has("opening_hours") ? tags.get("opening_hours").asText() : null;
            LocalTime[] openClose = deriveOpenClose(hoursText);
            activities.add(new Activity(
                    id, name.trim(), category,
                    new Location(lat, lon, address),
                    4.0, duration,
                    openClose[0], openClose[1],
                    ioType, riskLevel(ioType), hoursText));
            idx++;
        }
        return activities;
    }

    /**
     * Prefers an English name for a place when OSM maps one, so foreign-language places
     * appear translated. Falls back from name:en to int_name to the original name tag.
     */
    private static String englishName(JsonNode tags) {
        String english = tags.has("name:en") ? tags.get("name:en").asText() : null;
        if (english != null && !english.trim().isEmpty()) return english.trim();
        String international = tags.has("int_name") ? tags.get("int_name").asText() : null;
        if (international != null && !international.trim().isEmpty()) return international.trim();
        String original = tags.has("name") ? tags.get("name").asText() : null;
        return original == null ? null : original.trim();
    }

    private ActivityCategory categorize(String amenity, String tourism, String shop) {
        if ("restaurant".equals(amenity)) return ActivityCategory.FOOD;
        if ("cafe".equals(amenity)) return ActivityCategory.COFFEE;
        if ("museum".equals(tourism)) return ActivityCategory.MUSEUM;
        if ("attraction".equals(tourism)) return ActivityCategory.ATTRACTION;
        if (!shop.isEmpty()) return ActivityCategory.SHOPPING;
        return ActivityCategory.ATTRACTION;
    }

    private IndoorOutdoorType inferIndoorOutdoor(ActivityCategory cat) {
        switch (cat) {
            case FOOD: case COFFEE: case MUSEUM: case SHOPPING:
                return IndoorOutdoorType.INDOOR;
            default:
                return IndoorOutdoorType.OUTDOOR;
        }
    }

    private String buildAddress(JsonNode tags) {
        StringBuilder addr = new StringBuilder();
        if (tags.has("addr:housenumber")) addr.append(tags.get("addr:housenumber").asText()).append(" ");
        if (tags.has("addr:street")) addr.append(tags.get("addr:street").asText());
        if (tags.has("addr:city")) {
            if (addr.length() > 0) addr.append(", ");
            addr.append(tags.get("addr:city").asText());
        }
        if (addr.length() == 0) addr.append(tags.has("name") ? tags.get("name").asText() : "Unknown");
        return addr.toString();
    }

    private int estimateDuration(ActivityCategory category) {
        switch (category) {
            case FOOD: return 60;
            case MUSEUM: return 120;
            case OUTDOOR: return 90;
            case SHOPPING: return 60;
            case COFFEE: return 30;
            case ATTRACTION: return 90;
            default: return 60;
        }
    }

    private String riskLevel(IndoorOutdoorType type) {
        return type == IndoorOutdoorType.INDOOR ? "Low" : "Medium";
    }
private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class OverpassBusyException extends IOException {
        OverpassBusyException(String message) {
            super(message);
        }
    }

    /**
     * Derives a single representative open/close window from an OSM opening_hours value.
     * Falls back to a 09:00-21:00 default when the value is missing or unparseable so that
     * scheduling logic always has a valid window.
     */
    private static LocalTime[] deriveOpenClose(String openingHours) {
        LocalTime defaultOpen = LocalTime.of(9, 0);
        LocalTime defaultClose = LocalTime.of(21, 0);
        if (openingHours == null || openingHours.trim().isEmpty()) {
            return new LocalTime[]{defaultOpen, defaultClose};
        }
        String text = openingHours.toLowerCase();
        if (text.contains("24/7")) {
            return new LocalTime[]{LocalTime.of(0, 0), LocalTime.of(23, 59)};
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")
                .matcher(text);
        LocalTime earliest = null;
        LocalTime latest = null;
        while (matcher.find()) {
            LocalTime start = LocalTime.of(parseHour(matcher.group(1)), parseMinute(matcher.group(2)));
            LocalTime end = LocalTime.of(parseHour(matcher.group(3)), parseMinute(matcher.group(4)));
            if (start.isAfter(end)) continue;
            if (earliest == null || start.isBefore(earliest)) earliest = start;
            if (latest == null || end.isAfter(latest)) latest = end;
        }
        if (earliest == null || latest == null) {
            return new LocalTime[]{defaultOpen, defaultClose};
        }
        return new LocalTime[]{earliest, latest};
    }

    private static int parseHour(String token) {
        try {
            int value = Integer.parseInt(token);
            return value >= 0 && value <= 23 ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseMinute(String token) {
        try {
            int value = Integer.parseInt(token);
            return value >= 0 && value <= 59 ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
