package interface_adapter.places;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.OpeningHours;
import use_case.ports.PlacesService;

/** PlacesService adapter backed by OpenStreetMap Nominatim (geocoding) and Overpass (POI search). */
public final class NominatimPlacesService implements PlacesService {
    private static final URI GEOCODING_ENDPOINT =
            URI.create("https://nominatim.openstreetmap.org/search");
    private static final List<URI> OVERPASS_ENDPOINTS = List.of(
            URI.create("https://overpass-api.de/api/interpreter"),
            URI.create("https://overpass.kumi.systems/api/interpreter"),
            URI.create("https://overpass.private.coffee/api/interpreter"));
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration OVERPASS_TIMEOUT = Duration.ofSeconds(40);
    private static final long MAX_OVERALL_WAIT_MILLIS = 50_000L;
    private static final double SEARCH_RADIUS_METERS = 1500;
    private static final int MAX_RESULTS = 25;
    private static final int MAX_BUSY_RETRIES = 3;
    private static final long[] BUSY_RETRY_DELAY_MILLIS =
            {300L, 800L, 2000L};
    private static final long DEFAULT_RATE_LIMIT_WAIT_MILLIS = 5_000L;
    private static final long MAX_RETRY_AFTER_MILLIS = 15_000L;


    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI geocodingEndpoint;
    private final URI overpassEndpoint;
    private final boolean tryFallbacks;
    private final Map<String, List<Activity>> cache = new ConcurrentHashMap<>();
    private final Map<String, List<Activity>> boundsCache = new ConcurrentHashMap<>();
    private final Map<String, List<Activity>> namedSearchCache = new ConcurrentHashMap<>();

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
        final String key = destination.trim().toLowerCase();
        List<Activity> cached = cache.get(key);
        if (cached == null) {
            cached = searchUncached(destination);
            if (!cached.isEmpty()) {
                cache.put(key, cached);
            }
        }
        final String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.isEmpty()) {
            return new ArrayList<>(cached);
        }
        final List<Activity> result = filterByText(cached, needle);
        if (!result.isEmpty()) {
            return result;
        }

        final String namedKey = key + "|" + needle;
        List<Activity> discovered = namedSearchCache.get(namedKey);
        if (discovered == null) {
            discovered = searchNamedPlace(destination, query);
            namedSearchCache.put(namedKey, discovered);
            if (!discovered.isEmpty()) {
                cache.put(key, mergeById(cached, discovered));
            }
        }
        return filterByText(discovered, needle);
    }

    private static List<Activity> filterByText(List<Activity> activities, String needle) {
        final List<Activity> result = new ArrayList<>();
        for (Activity activity : activities) {
            if (activity.getName().toLowerCase().contains(needle)
                    || activity.getCategory().name().toLowerCase().contains(needle)
                    || activity.getLocation().getAddress().toLowerCase().contains(needle)) {
                result.add(activity);
            }
        }
        return result;
    }

    private List<Activity> searchNamedPlace(String destination, String query) {
        try {
            final JsonNode matches = geocodeResults(query.trim() + ", " + destination.trim(), 5);
            final StringBuilder selectors = new StringBuilder();
            for (JsonNode match : matches) {
                final String type = match.path("osm_type").asText();
                final long id = match.path("osm_id").asLong(-1);
                if (id > 0 && oneOf(type, "node", "way", "relation")) {
                    selectors.append(type).append('(').append(id).append(");");
                }
            }
            if (selectors.length() == 0) {
                return new ArrayList<>();
            }
            final String overpassQuery = "[out:json][timeout:30];(" + selectors + ");out center;";
            return parseElements(queryOverpass(overpassQuery), MAX_RESULTS);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        }
        catch (IOException | RuntimeException exception) {
            System.err.println("[NominatimPlaces] Named search failed: "
                    + exception.getMessage());
            return new ArrayList<>();
        }
    }

    private static List<Activity> mergeById(List<Activity> first, List<Activity> second) {
        final Map<String, Activity> merged = new java.util.LinkedHashMap<>();
        for (Activity activity : first) {
            merged.put(activity.getId(), activity);
        }
        for (Activity activity : second) {
            merged.put(activity.getId(), activity);
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public List<Activity> searchInBounds(double south, double west, double north, double east,
                                         int maxResults) {
        final String key = quantize(south, west, north, east);
        final List<Activity> cached = boundsCache.get(key);
        if (cached != null) {
            return cached;
        }
        final String overpassQuery = buildBoundingBoxQuery(south, west, north, east, maxResults);
        final JsonNode elements = queryOverpass(overpassQuery);
        final List<Activity> result = parseElements(elements, maxResults);
        if (!result.isEmpty()) {
            boundsCache.put(key, result);
        }
        return result;
    }

    /**
     * Groups nearby viewport windows onto a shared key so panning reuses one query result.
     * @param east the e as t value
     * @param north the n or th value
     * @param west the w es t value
     * @param south the s ou th value
     * @return the result of the operation
     */
    private static String quantize(double south, double west, double north, double east) {
        final long s = Math.round(south * 100);
        final long w = Math.round(west * 100);
        final long n = Math.round(north * 100);
        final long e = Math.round(east * 100);
        return s + "," + w + "," + n + "," + e;
    }

    private List<Activity> searchUncached(String destination) {
        try {
            final double[] coords = geocode(destination);
            final String overpassQuery = buildOverpassQuery(coords[0], coords[1]);
            System.out.println("[NominatimPlaces] Querying Overpass for " + destination + "...");
            final JsonNode elements = queryOverpass(overpassQuery);
            System.out.println("[NominatimPlaces] Got " + elements.size() + " elements");
            final List<Activity> result = parseElements(elements, MAX_RESULTS);
            System.out.println("[NominatimPlaces] Parsed " + result.size() + " activities");
            return result;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("[NominatimPlaces] Search interrupted");
            return new ArrayList<>();
        }
        catch (IOException | RuntimeException e) {
            System.err.println("[NominatimPlaces] Search failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private double[] geocode(String destination) throws IOException, InterruptedException {
        final JsonNode results = geocodeResults(destination, 1);
        if (results.isEmpty()) {
            throw new IOException("Nominatim found no location for: " + destination);
        }
        final JsonNode first = results.get(0);
        return new double[]{first.get("lat").asDouble(), first.get("lon").asDouble()};
    }

    private JsonNode geocodeResults(String query, int limit)
            throws IOException, InterruptedException {
        final URI uri = URI.create(geocodingEndpoint.toString()
                + "?q=" + encode(query) + "&format=json&limit=" + limit);
        final HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Trippy-CSC207/1.0")
                .GET().build();
        final HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Nominatim HTTP " + response.statusCode());
        }
        final JsonNode results = mapper.readTree(response.body());
        if (!results.isArray()) {
            throw new IOException("Invalid Nominatim response");
        }
        return results;
    }

    private String buildOverpassQuery(double lat, double lon) {
        final int r = (int) SEARCH_RADIUS_METERS;
        return "[out:json][timeout:30];"
            + "(" + activitySelectors("around:" + r + "," + lat + "," + lon) + ");"
            + "out center " + MAX_RESULTS + ";";
    }

    private String buildBoundingBoxQuery(double south, double west, double north, double east,
                                         int maxResults) {
        return "[out:json][timeout:30];"
            + "(" + activitySelectors("bbox:" + south + "," + west + "," + north
                    + "," + east) + ");"
            + "out center " + maxResults + ";";
    }

    private static String activitySelectors(String area) {
        return "nwr[\"amenity\"~\"^(restaurant|cafe|fast_food|food_court|pub|bar|biergarten|"
                + "ice_cream|bbq|internet_cafe|cinema|theatre|music_venue|nightclub|"
                + "events_venue|casino|arts_centre|exhibition_centre|planetarium|"
                + "community_centre|marketplace)$\"](" + area + ");"
                + "nwr[\"tourism\"~\"^(museum|gallery|artwork|attraction|aquarium|zoo|"
                + "theme_park|viewpoint|picnic_site|camp_site)$\"](" + area + ");"
                + "nwr[\"leisure\"~\"^(park|garden|nature_reserve|bowling_alley|escape_game|"
                + "amusement_arcade|sports_centre|fitness_centre|swimming_pool|golf_course|"
                + "pitch|track|ice_rink|miniature_golf|playground|dog_park|beach_resort|"
                + "marina|water_park)$\"](" + area + ");"
                + "nwr[\"natural\"~\"^(beach|waterfall|peak|cave_entrance|spring|hot_spring)$\"]("
                + area + ");"
                + "nwr[\"historic\"~\"^(castle|fort|ruins|monument|memorial|"
                + "archaeological_site|city_gate|manor)$\"](" + area + ");"
                + "nwr[\"boundary\"=\"national_park\"](" + area + ");"
                + "nwr[\"highway\"=\"trailhead\"](" + area + ");"
                + "nwr[\"man_made\"~\"^(observatory|tower)$\"](" + area + ");"
                + "nwr[\"attraction\"~\"^(roller_coaster|carousel|dark_ride)$\"](" + area + ");"
                + "nwr[\"shop\"](" + area + ");";
    }

    private JsonNode queryOverpass(String query) {
        // The server budget inside the query is 30s, so a slow-but-fine response can take
        // longer than the old 20s client timeout; allow it to. A hard overall deadline still
        // bounds the worst case when the public servers are down or overloaded.
        final long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(MAX_OVERALL_WAIT_MILLIS);
        int busyRetries = 0;
        while (System.nanoTime() < deadline) {
            boolean sawBusy = false;
            for (URI endpoint : overpassCandidates()) {
                if (System.nanoTime() >= deadline) {
                    return mapper.createArrayNode();
                }
                try {
                    return queryEndpoint(endpoint, query);
                }
                catch (OverpassBusyException busy) {
                    sawBusy = true;
                    System.err.println("[NominatimPlaces] Overpass " + endpoint.getHost()
                            + " unavailable (server busy, overloaded, or rate limited), will retry");
                    if (busy.retryAfterMillis() >= 0) {
                        // A rate limit means "wait this long", not "try again in 300ms".
                        final long wait = Math.min(busy.retryAfterMillis(),
                                Math.max(0L, deadline - System.nanoTime()) / 1_000_000L);
                        if (wait > 0) {
                            try {
                                Thread.sleep(wait);
                            }
                            catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                return mapper.createArrayNode();
                            }
                        }
                    }
                }
                catch (HttpTimeoutException timeout) {
                    sawBusy = true;
                    System.err.println("[NominatimPlaces] Overpass " + endpoint.getHost()
                            + " timed out, will retry");
                }
                catch (Exception exception) {
                    System.err.println("[NominatimPlaces] Overpass " + endpoint.getHost()
                            + " failed: " + exception.getMessage());
                }
            }
            if (!sawBusy || busyRetries >= MAX_BUSY_RETRIES) {
                return mapper.createArrayNode();
            }
            final long delay = BUSY_RETRY_DELAY_MILLIS[Math.min(busyRetries,
                    BUSY_RETRY_DELAY_MILLIS.length - 1)];
            busyRetries++;
            try {
                Thread.sleep(delay);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return mapper.createArrayNode();
            }
        }
        return mapper.createArrayNode();
    }

    private List<URI> overpassCandidates() {
        final List<URI> candidates = new ArrayList<>();
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
        final String body = "data=" + encode(query);
        final HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(OVERPASS_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Trippy-CSC207/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        final HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // 429 and 5xx mean the server is overloaded right now; that is worth retrying
            // with backoff rather than treating as terminal like a 4xx would be. A 429 may
            // carry Retry-After, and when it does the polite thing is to honor it.
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new OverpassBusyException("Overpass HTTP " + response.statusCode(),
                        retryAfterMillis(response));
            }
            throw new IOException("Overpass HTTP " + response.statusCode());
        }
        final String text = response.body();
        final String trimmed = text == null ? "" : text.strip();
        if (!trimmed.isEmpty() && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new OverpassBusyException("Overpass returned a non-JSON (overloaded) page", -1L);
        }
        final JsonNode tree = mapper.readTree(trimmed);
        return tree.has("elements") ? tree.get("elements") : mapper.createArrayNode();
    }

    /**
     * How long to wait after a rate-limit response: the server's Retry-After when present
     * (capped), a default when the status was 429 but no header arrived, and -1 for generic
     * overload so the caller falls back to its short backoff schedule.
      * @param response the r es po ns e value
      * @return the result of the operation
     */
    private long retryAfterMillis(HttpResponse<?> response) {
        if (response.statusCode() == 429) {
            final String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            if (retryAfter != null) {
                try {
                    final long seconds = Math.max(0L, Long.parseLong(retryAfter.trim()));
                    return Math.min(TimeUnit.SECONDS.toMillis(seconds), MAX_RETRY_AFTER_MILLIS);
                }
                catch (NumberFormatException ignored) {
                    // fall through to the default
                }
            }
            return DEFAULT_RATE_LIMIT_WAIT_MILLIS;
        }
        return -1L;
    }

    private List<Activity> parseElements(JsonNode elements, int limit) {
        final List<Activity> activities = new ArrayList<>();
        if (elements == null || !elements.isArray()) {
            return activities;
        }
        int idx = 0;
        for (JsonNode el : elements) {
            if (idx >= limit) {
                break;
            }
            final JsonNode center = el.path("center");
            final double lat = el.has("lat") ? el.get("lat").asDouble()
                    : center.path("lat").asDouble(Double.NaN);
            final double lon = el.has("lon") ? el.get("lon").asDouble()
                    : center.path("lon").asDouble(Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
                continue;
            }
            final JsonNode tags = el.has("tags") ? el.get("tags") : mapper.createObjectNode();
            final String name = englishName(tags);
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            final String amenity = tags.has("amenity") ? tags.get("amenity").asText() : "";
            final String tourism = tags.has("tourism") ? tags.get("tourism").asText() : "";
            final String shop = tags.has("shop") ? tags.get("shop").asText() : "";
            final String leisure = tag(tags, "leisure");
            final String natural = tag(tags, "natural");
            final String historic = tag(tags, "historic");
            final String boundary = tag(tags, "boundary");
            final String highway = tag(tags, "highway");
            final String manMade = tag(tags, "man_made");
            final String attraction = tag(tags, "attraction");
            final String museum = tag(tags, "museum");
            final ActivityCategory category = categorize(amenity, tourism, shop, leisure,
                    natural, historic, boundary, highway, manMade, attraction, museum);
            final IndoorOutdoorType ioType = inferIndoorOutdoor(category);
            final String address = buildAddress(tags);
            final String id = "osm-" + el.get("id").asLong();
            final int duration = estimateDuration(category);
            final String hoursText = tags.has("opening_hours") ? tags.get("opening_hours").asText() : null;
            final LocalTime[] openClose = deriveOpenClose(hoursText);
            // The same text, read a second way. deriveOpenClose above deliberately flattens
            // the week into one window so that every caller always has something valid;
            // OpeningHoursParser keeps the weekdays and the gaps, which is what the
            // scheduler needs and what the flattened window cannot express. Anything the
            // parser cannot fully understand comes back unknown, and the flattened window
            // stays in charge -- so this never makes a place less schedulable than before.
            final OpeningHours hours = OpeningHoursParser.parse(hoursText);
            activities.add(new Activity(
                    id, name.trim(), category,
                    new Location(lat, lon, address),
                    4.0, duration,
                    openClose[0], openClose[1],
                    ioType, riskLevel(ioType), hoursText, hours));
            idx++;
        }
        return activities;
    }

    /**
     * Prefers an English name for a place when OSM maps one, so foreign-language places
     * appear translated. Falls back from name:en to int_name to the original name tag.
      * @param tags the t ag s value
      * @return the result of the operation
     */
    private static String englishName(JsonNode tags) {
        final String english = tags.has("name:en") ? tags.get("name:en").asText() : null;
        if (english != null && !english.trim().isEmpty()) {
            return english.trim();
        }
        final String international = tags.has("int_name") ? tags.get("int_name").asText() : null;
        if (international != null && !international.trim().isEmpty()) {
            return international.trim();
        }
        final String original = tags.has("name") ? tags.get("name").asText() : null;
        return original == null ? null : original.trim();
    }

    private static String tag(JsonNode tags, String key) {
        return tags.has(key) ? tags.get(key).asText() : "";
    }

    private ActivityCategory categorize(String amenity, String tourism, String shop,
                                        String leisure, String natural, String historic,
                                        String boundary, String highway, String manMade,
                                        String attraction, String museum) {
        if (oneOf(amenity, "cinema", "theatre", "music_venue", "nightclub",
                "events_venue", "casino")
                || oneOf(leisure, "bowling_alley", "escape_game", "amusement_arcade")) {
            return ActivityCategory.ENTERTAINMENT;
        }
        if (oneOf(leisure, "park", "garden", "nature_reserve")
                || oneOf(natural, "beach", "waterfall", "peak", "cave_entrance")
                || "national_park".equals(boundary)) {
            return ActivityCategory.PARKS_NATURE;
        }
        if (oneOf(historic, "castle", "fort", "ruins", "monument", "memorial",
                "archaeological_site", "city_gate")) {
            return ActivityCategory.HISTORIC;
        }
        if (oneOf(leisure, "sports_centre", "fitness_centre", "swimming_pool",
                "golf_course", "pitch", "track", "ice_rink", "miniature_golf")) {
            return ActivityCategory.SPORTS_RECREATION;
        }
        if (oneOf(tourism, "gallery", "artwork")
                || oneOf(amenity, "arts_centre", "exhibition_centre")) {
            return ActivityCategory.ARTS_CULTURE;
        }
        if (oneOf(amenity, "restaurant", "fast_food", "food_court", "pub", "bar",
                "biergarten", "ice_cream", "bbq")
                || oneOf(shop, "bakery", "deli", "confectionery", "pastry", "cheese",
                "chocolate", "seafood")) {
            return ActivityCategory.FOOD;
        }
        if (oneOf(amenity, "cafe", "internet_cafe")
                || oneOf(shop, "coffee", "tea")) {
            return ActivityCategory.COFFEE;
        }
        if ("museum".equals(tourism) || !museum.isEmpty()) {
            return ActivityCategory.MUSEUM;
        }
        if (oneOf(leisure, "playground", "dog_park", "beach_resort", "marina")
                || oneOf(tourism, "viewpoint", "picnic_site", "camp_site")
                || oneOf(natural, "spring", "hot_spring")
                || "trailhead".equals(highway)) {
            return ActivityCategory.PARKS_NATURE;
        }
        if (!shop.isEmpty()) {
            return ActivityCategory.SHOPPING;
        }
        if (oneOf(tourism, "attraction", "aquarium", "zoo", "theme_park")
                || oneOf(amenity, "planetarium", "community_centre")
                || "water_park".equals(leisure)
                || oneOf(manMade, "observatory", "tower")
                || oneOf(attraction, "roller_coaster", "carousel", "dark_ride")) {
            return ActivityCategory.ATTRACTION;
        }
        return ActivityCategory.ATTRACTION;
    }

    private static boolean oneOf(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private IndoorOutdoorType inferIndoorOutdoor(ActivityCategory cat) {
        switch (cat) {
            case FOOD: case COFFEE: case MUSEUM: case SHOPPING:
            case ENTERTAINMENT: case ARTS_CULTURE:
                return IndoorOutdoorType.INDOOR;
            case SPORTS_RECREATION: case HISTORIC:
                return IndoorOutdoorType.MIXED;
            default:
                return IndoorOutdoorType.OUTDOOR;
        }
    }

    private String buildAddress(JsonNode tags) {
        final StringBuilder addr = new StringBuilder();
        if (tags.has("addr:housenumber")) {
            addr.append(tags.get("addr:housenumber").asText()).append(" ");
        }
        if (tags.has("addr:street")) {
            addr.append(tags.get("addr:street").asText());
        }
        if (tags.has("addr:city")) {
            if (addr.length() > 0) {
                addr.append(", ");
            }
            addr.append(tags.get("addr:city").asText());
        }
        if (addr.length() == 0) {
            addr.append(tags.has("name") ? tags.get("name").asText() : "Unknown");
        }
        return addr.toString();
    }

    private int estimateDuration(ActivityCategory category) {
        switch (category) {
            case FOOD: return 60;
            case MUSEUM: return 120;
            case SHOPPING: return 60;
            case COFFEE: return 30;
            case ATTRACTION: return 90;
            case ENTERTAINMENT: return 120;
            case PARKS_NATURE: return 90;
            case HISTORIC: return 90;
            case SPORTS_RECREATION: return 90;
            case ARTS_CULTURE: return 90;
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
        private final long retryAfterMillis;

        OverpassBusyException(String message, long retryAfterMillis) {
            super(message);
            this.retryAfterMillis = retryAfterMillis;
        }

        /**
         * Suggested wait before retrying, or -1 to fall back to the short backoff schedule.
         * @return the result of the operation
         */
        long retryAfterMillis() {
            return retryAfterMillis;
        }
    }

    /**
     * Derives a single representative open/close window from an OSM opening_hours value.
     * Falls back to a 09:00-21:00 default when the value is missing or unparseable so that
     * scheduling logic always has a valid window.
      * @param openingHours the o pe ni ng ho ur s value
      * @return the result of the operation
     */
    private static LocalTime[] deriveOpenClose(String openingHours) {
        final LocalTime defaultOpen = LocalTime.of(9, 0);
        final LocalTime defaultClose = LocalTime.of(21, 0);
        if (openingHours == null || openingHours.trim().isEmpty()) {
            return new LocalTime[]{defaultOpen, defaultClose};
        }
        final String text = openingHours.toLowerCase();
        if (text.contains("24/7")) {
            return new LocalTime[]{LocalTime.of(0, 0), LocalTime.of(23, 59)};
        }
        final java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")
                .matcher(text);
        LocalTime earliest = null;
        LocalTime latest = null;
        while (matcher.find()) {
            final LocalTime start = LocalTime.of(parseHour(matcher.group(1)), parseMinute(matcher.group(2)));
            final LocalTime end = LocalTime.of(parseHour(matcher.group(3)), parseMinute(matcher.group(4)));
            if (start.isAfter(end)) {
                continue;
            }
            if (earliest == null || start.isBefore(earliest)) {
                earliest = start;
            }
            if (latest == null || end.isAfter(latest)) {
                latest = end;
            }
        }
        if (earliest == null || latest == null) {
            return new LocalTime[]{defaultOpen, defaultClose};
        }
        return new LocalTime[]{earliest, latest};
    }

    private static int parseHour(String token) {
        try {
            final int value = Integer.parseInt(token);
            return value >= 0 && value <= 23 ? value : 0;
        }
        catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static int parseMinute(String token) {
        try {
            final int value = Integer.parseInt(token);
            return value >= 0 && value <= 59 ? value : 0;
        }
        catch (NumberFormatException exception) {
            return 0;
        }
    }
}
