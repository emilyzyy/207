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

/** PlacesService adapter backed by OpenStreetMap Nominatim (geocoding) and Overpass (POI search). */
public final class NominatimPlacesService implements PlacesService {
    private static final URI OVERPASS_ENDPOINT =
            URI.create("https://overpass.kumi.systems/api/interpreter");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final double SEARCH_RADIUS_METERS = 1500;
    private static final int MAX_RESULTS = 25;

    private final HttpClient client;
    private final ObjectMapper mapper;

    public NominatimPlacesService() {
        this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), new ObjectMapper());
    }

    NominatimPlacesService(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public List<Activity> search(String destination, String query) {
        if (destination == null || destination.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            double[] coords = geocode(destination);
            String overpassQuery = buildOverpassQuery(coords[0], coords[1]);
            System.out.println("[NominatimPlaces] Querying Overpass for " + destination + "...");
            JsonNode elements = queryOverpass(overpassQuery);
            System.out.println("[NominatimPlaces] Got " + elements.size() + " elements");
            List<Activity> result = parseElements(elements);
            System.out.println("[NominatimPlaces] Parsed " + result.size() + " activities");
            return result;
        } catch (Exception e) {
            System.err.println("[NominatimPlaces] Search failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private double[] geocode(String destination) throws IOException, InterruptedException {
        URI uri = URI.create("https://nominatim.openstreetmap.org/search"
                + "?q=" + encode(destination) + "&format=json&limit=1");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "CloseAI-CSC207/1.0")
                .GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode results = mapper.readTree(response.body());
        if (!results.isArray() || results.isEmpty()) {
            throw new IOException("Nominatim found no location for: " + destination);
        }
        JsonNode first = results.get(0);
        return new double[]{first.get("lat").asDouble(), first.get("lon").asDouble()};
    }

    private String buildOverpassQuery(double lat, double lon) {
        int r = (int) SEARCH_RADIUS_METERS;
        return "[out:json][timeout:12];"
            + "("
            + "node[\"amenity\"=\"restaurant\"](around:" + r + "," + lat + "," + lon + ");"
            + "node[\"amenity\"=\"cafe\"](around:" + r + "," + lat + "," + lon + ");"
            + "node[\"tourism\"=\"museum\"](around:" + r + "," + lat + "," + lon + ");"
            + "node[\"tourism\"=\"attraction\"](around:" + r + "," + lat + "," + lon + ");"
            + "node[\"shop\"](around:" + r + "," + lat + "," + lon + ");"
            + ");"
            + "out body " + MAX_RESULTS + ";";
    }

    private JsonNode queryOverpass(String query) throws IOException, InterruptedException {
        String body = "data=" + encode(query);
        HttpRequest request = HttpRequest.newBuilder(OVERPASS_ENDPOINT)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "CloseAI-CSC207/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            System.err.println("[NominatimPlaces] Overpass HTTP " + response.statusCode());
            return mapper.createArrayNode();
        }
        JsonNode tree = mapper.readTree(response.body());
        return tree.has("elements") ? tree.get("elements") : mapper.createArrayNode();
    }

    private List<Activity> parseElements(JsonNode elements) {
        List<Activity> activities = new ArrayList<>();
        if (elements == null || !elements.isArray()) return activities;
        int idx = 0;
        for (JsonNode el : elements) {
            if (idx >= MAX_RESULTS) break;
            double lat = el.has("lat") ? el.get("lat").asDouble() : 0;
            double lon = el.has("lon") ? el.get("lon").asDouble() : 0;
            if (!Double.isFinite(lat) || !Double.isFinite(lon)) continue;
            JsonNode tags = el.has("tags") ? el.get("tags") : mapper.createObjectNode();
            String name = tags.has("name") ? tags.get("name").asText() : null;
            if (name == null || name.trim().isEmpty()) continue;
            String amenity = tags.has("amenity") ? tags.get("amenity").asText() : "";
            String tourism = tags.has("tourism") ? tags.get("tourism").asText() : "";
            String shop = tags.has("shop") ? tags.get("shop").asText() : "";
            ActivityCategory category = categorize(amenity, tourism, shop);
            IndoorOutdoorType ioType = inferIndoorOutdoor(category);
            String address = buildAddress(tags);
            String id = "osm-" + el.get("id").asLong();
            int duration = estimateDuration(category);
            activities.add(new Activity(
                    id, name.trim(), category,
                    new Location(lat, lon, address),
                    4.0, duration,
                    LocalTime.of(9, 0), LocalTime.of(21, 0),
                    ioType, riskLevel(ioType)));
            idx++;
        }
        return activities;
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
}
