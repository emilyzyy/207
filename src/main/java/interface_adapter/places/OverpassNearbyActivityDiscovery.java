package interface_adapter.places;

import use_case.ports.DestinationGeocoder;
import use_case.ports.NearbyActivityDiscovery;
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
import java.util.concurrent.TimeUnit;

/** Overpass adapter dedicated to set-based nearby and viewport discovery. */
public final class OverpassNearbyActivityDiscovery implements NearbyActivityDiscovery {
    private static final List<URI> DEFAULT_ENDPOINTS = List.of(
            URI.create("https://overpass-api.de/api/interpreter"),
            URI.create("https://overpass.kumi.systems/api/interpreter"),
            URI.create("https://overpass.private.coffee/api/interpreter"));
    private static final String USER_AGENT =
            "Trippy-CSC207/1.0 (academic project; github.com/emilyzyy/207)";
    /** Per-endpoint ceiling; the overall deadline below still bounds the total. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    /** Hard ceiling for the whole endpoint-retry sequence so discovery never hangs. */
    private static final long MAX_OVERALL_WAIT_MILLIS = 10_000L;
    private static final long MAX_RETRY_AFTER_MILLIS = 4_000L;
    private static final long DEFAULT_RATE_LIMIT_WAIT_MILLIS = 1_000L;
    private final HttpClient http;
    private final ObjectMapper json;
    private final OsmActivityMapper mapper;
    private final DestinationGeocoder geocoder;
    private final List<URI> endpoints;
    private final Map<String, List<Activity>> cache = new ConcurrentHashMap<>();
    /** Prevents trip enrichment, map loading, and Search from flooding Overpass in parallel. */
    private final Object discoveryLock = new Object();

    public OverpassNearbyActivityDiscovery(DestinationGeocoder geocoder) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                new ObjectMapper(), geocoder, configuredEndpoints());
    }

    OverpassNearbyActivityDiscovery(HttpClient http, ObjectMapper json,
                                    DestinationGeocoder geocoder, List<URI> endpoints) {
        this.http = http;
        this.json = json;
        this.mapper = new OsmActivityMapper(json);
        this.geocoder = geocoder;
        this.endpoints = new ArrayList<>(endpoints);
    }

    @Override
    public List<Activity> around(String destination, int limit) {
        String key = "around|" + normalize(destination) + "|" + limit;
        List<Activity> cached = cache.get(key);
        if (cached != null) return new ArrayList<>(cached);
        GeoPoint center = geocoder.geocode(destination);
        String area = "around:" + center.getDiscoveryRadiusMeters() + ","
                + center.getLatitude() + "," + center.getLongitude();
        return coordinatedLoad(key, query(area, limit), limit);
    }

    @Override
    public List<Activity> inBounds(double south, double west, double north, double east, int limit) {
        String key = "bounds|" + Math.round(south * 100) + "," + Math.round(west * 100)
                + "," + Math.round(north * 100) + "," + Math.round(east * 100) + "|" + limit;
        List<Activity> cached = cache.get(key);
        if (cached != null) return new ArrayList<>(cached);
        String area = "bbox:" + south + "," + west + "," + north + "," + east;
        return coordinatedLoad(key, query(area, limit), limit);
    }

    /**
     * Serializes public Overpass access and repeats the cache check after waiting. A trip opening
     * starts enrichment and viewport discovery almost together; without this boundary, a Search
     * click can launch a duplicate request before the first call has populated the cache.
     */
    private List<Activity> coordinatedLoad(String key, String query, int limit) {
        synchronized (discoveryLock) {
            List<Activity> cached = cache.get(key);
            if (cached != null) return new ArrayList<>(cached);
            return loadAndCache(key, query, limit);
        }
    }

    private List<Activity> loadAndCache(String key, String query, int limit) {
        JsonNode elements = request(query);
        Map<String, Activity> unique = new LinkedHashMap<>();
        for (JsonNode element : elements) {
            mapper.fromOverpass(element).ifPresent(activity -> unique.put(activity.getId(), activity));
            if (unique.size() >= limit) break;
        }
        List<Activity> result = new ArrayList<>(unique.values());
        // A transient empty Overpass response must not poison this destination for the
        // remainder of the session. Successful results are stable enough to cache.
        if (!result.isEmpty()) cache.put(key, new ArrayList<>(result));
        return result;
    }

    private JsonNode request(String query) {
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(MAX_OVERALL_WAIT_MILLIS);
        PlaceSearchException last = null;
        JsonNode validEmptyResponse = null;
        for (URI endpoint : endpoints) {
            long remainingMillis = remainingMillis(deadline);
            if (remainingMillis <= 0) {
                break;
            }
            try {
                String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofMillis(Math.min(
                                REQUEST_TIMEOUT.toMillis(), remainingMillis)))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", USER_AGENT)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 429) {
                    last = new PlaceSearchException(SearchFailure.RATE_LIMITED,
                            "Overpass is rate limited");
                    sleepRetryAfter(response, deadline);
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    last = new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                            "Overpass HTTP " + response.statusCode());
                    continue;
                }
                String text = response.body() == null ? "" : response.body().trim();
                if (!text.startsWith("{") && !text.startsWith("[")) {
                    last = new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                            "Overpass returned a non-JSON response");
                    continue;
                }
                JsonNode root = json.readTree(text);
                JsonNode elements = root.has("elements")
                        ? root.path("elements") : json.createArrayNode();
                if (elements.isArray() && elements.isEmpty()) {
                    // Public replicas occasionally return an empty successful response while
                    // under load. Confirm it with the next configured replica before accepting it.
                    validEmptyResponse = elements;
                    continue;
                }
                return elements;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                        "Nearby discovery was interrupted", exception);
            } catch (IOException | RuntimeException exception) {
                if (exception instanceof PlaceSearchException) {
                    last = (PlaceSearchException) exception;
                } else {
                    last = new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                            "Overpass is unavailable", exception);
                }
            }
        }
        if (validEmptyResponse != null) return validEmptyResponse;
        throw last == null ? new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                "No Overpass endpoint is configured") : last;
    }

    /** How much of the overall deadline is left, in milliseconds (at least zero). */
    private static long remainingMillis(long deadline) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
    }

    /** Honours Retry-After when present, otherwise a short default, all within the deadline. */
    private void sleepRetryAfter(HttpResponse<?> response, long deadline)
            throws InterruptedException {
        long waitMillis = DEFAULT_RATE_LIMIT_WAIT_MILLIS;
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) {
            try {
                waitMillis = Math.min(
                        TimeUnit.SECONDS.toMillis(Math.max(0L, Long.parseLong(retryAfter.trim()))),
                        MAX_RETRY_AFTER_MILLIS);
            } catch (NumberFormatException ignored) {
                // fall back to the default wait
            }
        }
        long allowed = Math.min(waitMillis, remainingMillis(deadline));
        if (allowed > 0) {
            Thread.sleep(allowed);
        }
    }

    private static String query(String area, int limit) {
        return "[out:json][timeout:25];(" + selectors(area) + ");out center " + limit + ";";
    }

    /** Selector groups remain separate and testable while sharing one network request. */
    static String selectors(String area) {
        return foodAndCoffee(area) + cultureAndEntertainment(area)
                + natureAndRecreation(area) + shoppingAndHistoric(area);
    }

    private static String foodAndCoffee(String a) {
        return "nwr[\"amenity\"~\"^(restaurant|cafe|fast_food|food_court|pub|bar|biergarten|ice_cream|bbq|internet_cafe)$\"](" + a + ");"
                + "nwr[\"shop\"~\"^(bakery|deli|confectionery|pastry|cheese|chocolate|seafood|coffee|tea)$\"](" + a + ");";
    }

    private static String cultureAndEntertainment(String a) {
        return "nwr[\"tourism\"~\"^(museum|gallery|artwork|attraction|aquarium|zoo|theme_park)$\"](" + a + ");"
                + "nwr[\"amenity\"~\"^(cinema|theatre|music_venue|nightclub|events_venue|casino|arts_centre|exhibition_centre|planetarium|community_centre)$\"](" + a + ");"
                + "nwr[\"attraction\"~\"^(roller_coaster|carousel|dark_ride)$\"](" + a + ");";
    }

    private static String natureAndRecreation(String a) {
        return "nwr[\"leisure\"~\"^(park|garden|nature_reserve|bowling_alley|escape_game|amusement_arcade|sports_centre|fitness_centre|swimming_pool|golf_course|pitch|track|ice_rink|miniature_golf|playground|dog_park|beach_resort|marina|water_park)$\"](" + a + ");"
                + "nwr[\"natural\"~\"^(beach|waterfall|peak|cave_entrance|spring|hot_spring)$\"](" + a + ");"
                + "nwr[\"tourism\"~\"^(viewpoint|picnic_site|camp_site)$\"](" + a + ");"
                + "nwr[\"boundary\"=\"national_park\"](" + a + ");"
                + "nwr[\"highway\"=\"trailhead\"](" + a + ");";
    }

    private static String shoppingAndHistoric(String a) {
        return "nwr[\"shop\"](" + a + ");"
                + "nwr[\"historic\"~\"^(castle|fort|ruins|monument|memorial|archaeological_site|city_gate|manor)$\"](" + a + ");"
                + "nwr[\"man_made\"~\"^(observatory|tower)$\"](" + a + ");";
    }

    private static List<URI> configuredEndpoints() {
        String configured = System.getProperty("trippy.overpass.endpoint", "").trim();
        if (!configured.isEmpty()) return List.of(URI.create(configured));
        return DEFAULT_ENDPOINTS;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
