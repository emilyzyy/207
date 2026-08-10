package interface_adapter.places;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entity.entities.Activity;
import entity.valueobjects.GeoPoint;
import use_case.ports.DestinationGeocoder;
import use_case.ports.NearbyActivityDiscovery;
import use_case.search.PlaceSearchException;
import use_case.search.SearchFailure;

/** Overpass adapter dedicated to set-based nearby and viewport discovery. */
public final class OverpassNearbyActivityDiscovery implements NearbyActivityDiscovery {
    /**
     * Mirrors are tried in order under a shared deadline. maps.mail.ru is first because
     * overpass-api.de's DNS can return a first A record that is unreachable from a given
     * network (the JVM HttpClient does not fall through to the next A record), so reaching
     * the canonical host costs an 8 s connect timeout on every request. The other hosts stay
     * as fallbacks in case the working mirror is itself unavailable.
     */
    private static final List<URI> DEFAULT_ENDPOINTS = List.of(
            URI.create("https://maps.mail.ru/osm/tools/overpass/api/interpreter"),
            URI.create("https://overpass-api.de/api/interpreter"),
            URI.create("https://overpass.private.coffee/api/interpreter"),
            URI.create("https://overpass.kumi.systems/api/interpreter"));
    private static final String USER_AGENT =
            "Trippy-CSC207/1.0 (academic project; github.com/emilyzyy/207)";
    /** Per-endpoint ceiling; the overall deadline below still bounds the total. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    /** Hard ceiling for the whole endpoint-retry sequence so discovery never hangs. */
    private static final long MAX_OVERALL_WAIT_MILLIS = 55_000L;
    private static final long MAX_RETRY_AFTER_MILLIS = 4_000L;
    private static final long DEFAULT_RATE_LIMIT_WAIT_MILLIS = 1_000L;
    /**
     * The discovery radius is bounded by the municipal bounding box, which for a city can reach
     * the geocoder's 3000 m ceiling. Around queries at that size are too heavy for the public
     * Overpass replicas: they time out server-side or return 504. 1500 m reliably answers with a
     * full result set within the client budget, so discovery is clamped here.
     */
    private static final int MAX_AROUND_RADIUS_METERS = 1_500;
    /**
     * Places per query window are capped at 25, the value that kept discovery responsive on the
     * public replicas. Asking for 100 makes the response larger and the server-side evaluation
     * heavier without helping the itinerary; a smaller answer arrives within the client budget.
     */
    private static final int MAX_WINDOW_RESULTS = 25;
    private final HttpClient http;
    private final ObjectMapper json;
    private final OsmActivityMapper mapper;
    private final DestinationGeocoder geocoder;
    private final List<URI> endpoints;
    private final Map<String, List<Activity>> cache = new ConcurrentHashMap<>();
    /**
     * Deduplicates concurrent lookups per key. Identical in-flight requests share one lock, but
     * unrelated keys (trip enrichment, a viewport cell, and a Search click) run in parallel
     * instead of queueing behind one another.
     */
    private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();
    /**
     * The circle a successful whole-city around query covered, per destination. Viewport boxes
     * that fall inside it can be answered from these cached results instead of issuing more
     * Overpass requests (trip enrichment and the map's first cells otherwise re-query the same
     * city centre).
     */
    private final Map<String, AroundCoverage> aroundCoverage = new ConcurrentHashMap<>();

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
        final int window = windowLimit(limit);
        final String key = "around|" + normalize(destination) + "|" + window;
        final List<Activity> cached = cache.get(key);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        final GeoPoint center = geocoder.geocode(destination);
        final int radius = Math.min(center.getDiscoveryRadiusMeters(), MAX_AROUND_RADIUS_METERS);
        final String area = "around:" + radius + ","
                + center.getLatitude() + "," + center.getLongitude();
        final List<Activity> result = coordinatedLoad(key, query(area, window), window);
        if (!result.isEmpty()) {
            aroundCoverage.put(normalize(destination), new AroundCoverage(
                    center.getLatitude(), center.getLongitude(), radius, result));
        }
        return result;
    }

    @Override
    public List<Activity> inBounds(double south, double west, double north, double east, int limit) {
        final int window = windowLimit(limit);
        final String key = "bounds|" + Math.round(south * 100) + "," + Math.round(west * 100)
                + "," + Math.round(north * 100) + "," + Math.round(east * 100) + "|" + window;
        final List<Activity> cached = cache.get(key);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        final double centerLat = (south + north) / 2;
        final double centerLng = (west + east) / 2;
        final int radius = viewportRadius(south, west, north, east);
        final String area = "around:" + radius + ","
                + centerLat + "," + centerLng;
        return coordinatedLoad(key, query(area, window), window);
    }

    @Override
    public List<Activity> cachedInBounds(String destination,
                                         double south, double west, double north, double east) {
        final AroundCoverage coverage = aroundCoverage.get(normalize(destination));
        if (coverage == null || !covers(coverage, south, west, north, east)) {
            return null;
        }
        final List<Activity> inBox = new ArrayList<>();
        for (Activity activity : coverage.activities) {
            if (activity.getLocation() == null) {
                continue;
            }
            final double lat = activity.getLocation().getLatitude();
            final double lng = activity.getLocation().getLongitude();
            if (lat >= south && lat <= north && lng >= west && lng <= east) {
                inBox.add(activity);
            }
        }
        return inBox;
    }

    /**
     * True when the whole box, including its corners, lies inside the coverage circle.
     * @param coverage the c ov er ag e value
     * @return the result of the operation
     */
    private static boolean covers(AroundCoverage coverage,
                                  double south, double west, double north, double east) {
        final double centerLat = (south + north) / 2;
        final double centerLng = (west + east) / 2;
        final double halfWidth = (east - west) * 111_320.0
                * Math.cos(Math.toRadians(centerLat)) / 2.0;
        final double halfHeight = (north - south) * 111_320.0 / 2.0;
        final double boxHalfDiagonal = Math.hypot(halfWidth, halfHeight);
        final double distance = distanceMeters(coverage.latitude, coverage.longitude,
                centerLat, centerLng);
        return distance + boxHalfDiagonal <= coverage.radiusMeters;
    }

    /**
     * Great-circle distance in metres (Haversine).
     * @param lng2 the l ng2 value
     * @param lat2 the l at2 value
     * @param lng1 the l ng1 value
     * @param lat1 the l at1 value
     * @return the result of the operation
     */
    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        final double dLat = Math.toRadians(lat2 - lat1);
        final double dLng = Math.toRadians(lng2 - lng1);
        final double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6_371_000.0 * c;
    }

    private static final class AroundCoverage {
        final double latitude;
        final double longitude;
        final double radiusMeters;
        final List<Activity> activities;

        AroundCoverage(double latitude, double longitude,
                       double radiusMeters, List<Activity> activities) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.radiusMeters = radiusMeters;
            this.activities = activities;
        }
    }

    /**
     * The visible box is queried as one around at its centre, which answers reliably on the
     * public replicas (a whole-box bbox query does not). The radius is clamped to the reliable
     * ceiling, so a wide view fills around its centre while panning pulls in the neighbouring
     * boxes. Each box is cached under its own key, so revisiting a seen view is instant.
      * @param west the w es t value
      * @param south the s ou th value
      * @return the result of the operation
     */
    private static int viewportRadius(double south, double west,
                                      double north, double east) {
        final double metersPerDegreeLng = 111_320.0
                * Math.cos(Math.toRadians((south + north) / 2));
        final double halfWidthMeters = (east - west) * metersPerDegreeLng / 2.0;
        final double halfHeightMeters = (north - south) * 111_320.0 / 2.0;
        final double halfMin = Math.max(500.0, Math.min(halfWidthMeters, halfHeightMeters));
        return (int) Math.round(Math.min(MAX_AROUND_RADIUS_METERS, halfMin));
    }

    private static int windowLimit(int limit) {
        return Math.min(Math.max(1, limit), MAX_WINDOW_RESULTS);
    }
    /**
     * Deduplicates repeated lookups for the same key and repeats the cache check after waiting.
     * A trip opening starts enrichment and viewport discovery almost together; without this
     * boundary, a Search click can launch a duplicate request before the first call has populated
     * the cache. Locking is per key, so unrelated lookups are not blocked by an in-flight one.
      * @param query the q ue ry value
      * @param limit the l im it value
      * @param key the k ey value
      * @return the result of the operation
     */

    private List<Activity> coordinatedLoad(String key, String query, int limit) {
        return coordinatedLoad(key, () -> loadAndCache(key, query, limit));
    }

    private List<Activity> coordinatedLoad(String key,
                                           java.util.function.Supplier<List<Activity>> loader) {
        List<Activity> cached = cache.get(key);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        final Object lock = keyLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                cached = cache.get(key);
                if (cached != null) {
                    return new ArrayList<>(cached);
                }
                return loader.get();
            }
            finally {
                keyLocks.remove(key, lock);
            }
        }
    }

    private List<Activity> loadAndCache(String key, String query, int limit) {
        final List<Activity> result = fetch(query, limit);
        // A transient empty Overpass response must not poison this destination for the
        // remainder of the session. Successful results are stable enough to cache.
        if (!result.isEmpty()) {
            cache.put(key, new ArrayList<>(result));
        }
        return result;
    }

    private List<Activity> fetch(String query, int limit) {
        final JsonNode elements = request(query);
        final Map<String, Activity> unique = new LinkedHashMap<>();
        for (JsonNode element : elements) {
            mapper.fromOverpass(element).ifPresent(activity -> unique.put(activity.getId(), activity));
            if (unique.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private JsonNode request(String query) {
        final long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(MAX_OVERALL_WAIT_MILLIS);
        PlaceSearchException last = null;
        JsonNode validEmptyResponse = null;
        for (URI endpoint : endpoints) {
            final long remainingMillis = remainingMillis(deadline);
            if (remainingMillis <= 0) {
                break;
            }
            try {
                final String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
                final HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofMillis(Math.min(
                                REQUEST_TIMEOUT.toMillis(), remainingMillis)))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", USER_AGENT)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                final HttpResponse<String> response = http.send(request,
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
                final String text = response.body() == null ? "" : response.body().trim();
                if (!text.startsWith("{") && !text.startsWith("[")) {
                    last = new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                            "Overpass returned a non-JSON response");
                    continue;
                }
                final JsonNode root = json.readTree(text);
                final String remark = root.path("remark").asText("");
                if (remark.contains("timed out") || remark.contains("runtime error")) {
                    // Overpass answers a server-side timeout with HTTP 200, an empty elements
                    // array and a remark such as "Query timed out". That is a failure, not a
                    // legitimate "no places here" answer, so try the next configured replica.
                    last = new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                            "Overpass query failed: " + remark);
                    continue;
                }
                final JsonNode elements = root.has("elements")
                        ? root.path("elements") : json.createArrayNode();
                if (elements.isArray() && elements.isEmpty()) {
                    // Public replicas occasionally return an empty successful response while
                    // under load. Confirm it with the next configured replica before accepting it.
                    validEmptyResponse = elements;
                    continue;
                }
                return elements;
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                        "Nearby discovery was interrupted", exception);
            }
            catch (IOException | RuntimeException exception) {
                if (exception instanceof PlaceSearchException) {
                    last = (PlaceSearchException) exception;
                }
                else {
                    last = new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                            "Overpass is unavailable", exception);
                }
            }
        }
        if (validEmptyResponse != null) {
            return validEmptyResponse;
        }
        throw last == null ? new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                "No Overpass endpoint is configured") : last;
    }

    /**
     * How much of the overall deadline is left, in milliseconds (at least zero).
     * @param deadline the d ea dl in e value
     * @return the result of the operation
     */
    private static long remainingMillis(long deadline) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
    }
    /**
     * Honours Retry-After when present, otherwise a short default, all within the deadline.
     * @param deadline the d ea dl in e value
     * @param response the r es po ns e value
     */

    private void sleepRetryAfter(HttpResponse<?> response, long deadline)
            throws InterruptedException {
        long waitMillis = DEFAULT_RATE_LIMIT_WAIT_MILLIS;
        final String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) {
            try {
                waitMillis = Math.min(
                        TimeUnit.SECONDS.toMillis(Math.max(0L, Long.parseLong(retryAfter.trim()))),
                        MAX_RETRY_AFTER_MILLIS);
            }
            catch (NumberFormatException ignored) {
                // fall back to the default wait
            }
        }
        final long allowed = Math.min(waitMillis, remainingMillis(deadline));
        if (allowed > 0) {
            Thread.sleep(allowed);
        }
    }

    private static String query(String area, int limit) {
        // Server-side timeout must stay comfortably below the client's REQUEST_TIMEOUT so a
        // replica that hits its limit responds 504/429 instead of hanging the client connection.
        return "[out:json][timeout:10];(" + selectors(area) + ");out center " + limit + ";";
    }

    /**
     * Selector groups remain separate and testable while sharing one network request.
     * @param area the a re a value
     * @return the result of the operation
     */
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
        // The bare nwr["shop"] selector matches every shop in the area, making the request far
        // too heavy for the public replicas: they either time out or rate-limit it. Specific shop
        // kinds (bakery, deli, coffee, ...) are already covered by foodAndCoffee; generic shops
        // without any of the mapped categories would be dropped by the mapper anyway.
        return "nwr[\"historic\"~\"^(castle|fort|ruins|monument|memorial|archaeological_site|city_gate|manor)$\"](" + a + ");"
                + "nwr[\"man_made\"~\"^(observatory|tower)$\"](" + a + ");";
    }

    private static List<URI> configuredEndpoints() {
        final String configured = System.getProperty("trippy.overpass.endpoint", "").trim();
        if (!configured.isEmpty()) {
            return List.of(URI.create(configured));
        }
        return DEFAULT_ENDPOINTS;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
