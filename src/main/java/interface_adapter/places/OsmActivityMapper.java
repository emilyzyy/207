package interface_adapter.places;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure translation from OSM/Nominatim JSON into the application's Activity entity. */
public final class OsmActivityMapper {
    private static final Pattern HOURS = Pattern.compile(
            "(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})");
    private final ObjectMapper json;

    public OsmActivityMapper(ObjectMapper json) {
        this.json = json == null ? new ObjectMapper() : json;
    }

    public Optional<Activity> fromOverpass(JsonNode element) {
        if (element == null) return Optional.empty();
        JsonNode center = element.path("center");
        double latitude = element.has("lat") ? element.path("lat").asDouble(Double.NaN)
                : center.path("lat").asDouble(Double.NaN);
        double longitude = element.has("lon") ? element.path("lon").asDouble(Double.NaN)
                : center.path("lon").asDouble(Double.NaN);
        JsonNode tags = element.path("tags");
        String name = preferredName(tags);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || blank(name)) {
            return Optional.empty();
        }
        ActivityCategory category = category(tags);
        if (category == null) return Optional.empty();
        String osmType = normalizeType(element.path("type").asText("node"));
        long osmId = element.path("id").asLong(-1L);
        if (osmId < 0) return Optional.empty();
        return Optional.of(activity("osm-" + osmType + "-" + osmId, name,
                category, latitude, longitude, address(tags, name), tags));
    }

    public Optional<Activity> fromNominatim(JsonNode place) {
        if (place == null) return Optional.empty();
        double latitude = place.path("lat").asDouble(Double.NaN);
        double longitude = place.path("lon").asDouble(Double.NaN);
        long osmId = place.path("osm_id").asLong(-1L);
        String osmType = normalizeType(place.path("osm_type").asText(""));
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || osmId < 0 || blank(osmType)) return Optional.empty();

        ObjectNode tags = json.createObjectNode();
        JsonNode extras = place.path("extratags");
        if (extras.isObject()) tags.setAll((ObjectNode) extras);
        String osmClass = place.has("category")
                ? place.path("category").asText() : place.path("class").asText();
        String osmValue = place.path("type").asText();
        if (!blank(osmClass) && !blank(osmValue)) tags.put(osmClass, osmValue);
        JsonNode names = place.path("namedetails");
        if (names.isObject()) {
            names.fields().forEachRemaining(entry -> tags.put(entry.getKey(), entry.getValue().asText()));
        }
        String name = place.path("name").asText();
        if (blank(name)) name = preferredName(tags);
        if (blank(name)) name = firstDisplayPart(place.path("display_name").asText());
        if (blank(name)) return Optional.empty();
        if (!tags.has("name")) tags.put("name", name);
        ActivityCategory category = category(tags);
        if (category == null) return Optional.empty();
        String address = place.path("display_name").asText(name);
        return Optional.of(activity("osm-" + osmType + "-" + osmId, name,
                category, latitude, longitude, address, tags));
    }

    private static Activity activity(String id, String name, ActivityCategory category,
                                     double latitude, double longitude, String address,
                                     JsonNode tags) {
        IndoorOutdoorType setting = setting(category, tags);
        String hoursText = text(tags, "opening_hours");
        LocalTime[] hours = hours(hoursText);
        // The parsed per-weekday reading, not just the raw text. Without it every place
        // discovered through this mapper reports unknown hours, which makes them all
        // permissively schedulable and quietly disables the opening-hours constraint.
        return new Activity(id, name.trim(), category,
                new Location(latitude, longitude, address),
                0.0, duration(category), hours[0], hours[1], setting,
                setting == IndoorOutdoorType.INDOOR ? "Low" : "Medium", hoursText,
                OpeningHoursParser.parse(hoursText));
    }

    static ActivityCategory category(JsonNode tags) {
        String amenity = text(tags, "amenity");
        String tourism = text(tags, "tourism");
        String shop = text(tags, "shop");
        String leisure = text(tags, "leisure");
        String natural = text(tags, "natural");
        String historic = text(tags, "historic");
        if (oneOf(amenity, "cinema", "theatre", "music_venue", "nightclub",
                "events_venue", "casino")
                || oneOf(leisure, "bowling_alley", "escape_game", "amusement_arcade"))
            return ActivityCategory.ENTERTAINMENT;
        if (oneOf(leisure, "park", "garden", "nature_reserve", "playground", "dog_park",
                "beach_resort", "marina")
                || oneOf(natural, "beach", "waterfall", "peak", "cave_entrance", "spring",
                "hot_spring") || "national_park".equals(text(tags, "boundary"))
                || "trailhead".equals(text(tags, "highway"))) return ActivityCategory.PARKS_NATURE;
        if (!blank(historic)) return ActivityCategory.HISTORIC;
        if (oneOf(leisure, "sports_centre", "fitness_centre", "swimming_pool", "golf_course",
                "pitch", "track", "ice_rink", "miniature_golf"))
            return ActivityCategory.SPORTS_RECREATION;
        if (oneOf(tourism, "gallery", "artwork")
                || oneOf(amenity, "arts_centre", "exhibition_centre"))
            return ActivityCategory.ARTS_CULTURE;
        if (oneOf(amenity, "restaurant", "fast_food", "food_court", "pub", "bar",
                "biergarten", "ice_cream", "bbq")
                || oneOf(shop, "bakery", "deli", "confectionery", "pastry", "cheese",
                "chocolate", "seafood")) return ActivityCategory.FOOD;
        if (oneOf(amenity, "cafe", "internet_cafe") || oneOf(shop, "coffee", "tea"))
            return ActivityCategory.COFFEE;
        if ("museum".equals(tourism) || !blank(text(tags, "museum")))
            return ActivityCategory.MUSEUM;
        if (!blank(shop)) return ActivityCategory.SHOPPING;
        if (oneOf(tourism, "attraction", "aquarium", "zoo", "theme_park", "viewpoint",
                "picnic_site", "camp_site")
                || oneOf(amenity, "planetarium", "community_centre")
                || "water_park".equals(leisure)
                || oneOf(text(tags, "man_made"), "observatory", "tower")
                || oneOf(text(tags, "attraction"), "roller_coaster", "carousel", "dark_ride"))
            return ActivityCategory.ATTRACTION;
        return null;
    }

    private static IndoorOutdoorType setting(ActivityCategory category, JsonNode tags) {
        String indoor = text(tags, "indoor");
        if ("yes".equals(indoor)) return IndoorOutdoorType.INDOOR;
        if ("no".equals(indoor)) return IndoorOutdoorType.OUTDOOR;
        switch (category) {
            case FOOD: case COFFEE: case MUSEUM: case SHOPPING:
            case ENTERTAINMENT: case ARTS_CULTURE: return IndoorOutdoorType.INDOOR;
            case PARKS_NATURE: return IndoorOutdoorType.OUTDOOR;
            default: return IndoorOutdoorType.MIXED;
        }
    }

    private static String preferredName(JsonNode tags) {
        String value = text(tags, "name:en");
        if (blank(value)) value = text(tags, "int_name");
        if (blank(value)) value = text(tags, "name");
        return value;
    }

    private static String address(JsonNode tags, String fallback) {
        StringBuilder value = new StringBuilder();
        append(value, text(tags, "addr:housenumber"), "");
        append(value, text(tags, "addr:street"), value.length() == 0 ? "" : " ");
        append(value, text(tags, "addr:city"), value.length() == 0 ? "" : ", ");
        return value.length() == 0 ? fallback : value.toString();
    }

    private static void append(StringBuilder target, String value, String separator) {
        if (!blank(value)) target.append(separator).append(value);
    }

    private static String firstDisplayPart(String display) {
        int comma = display.indexOf(',');
        return comma < 0 ? display.trim() : display.substring(0, comma).trim();
    }

    private static String normalizeType(String value) {
        String normalized = value == null ? "" : value.toLowerCase();
        if ("n".equals(normalized)) return "node";
        if ("w".equals(normalized)) return "way";
        if ("r".equals(normalized)) return "relation";
        return oneOf(normalized, "node", "way", "relation") ? normalized : "";
    }

    private static String text(JsonNode node, String key) {
        if (node == null || !node.has(key) || node.path(key).isNull()) return null;
        String value = node.path(key).asText();
        return blank(value) ? null : value.trim();
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static boolean oneOf(String value, String... choices) {
        for (String choice : choices) if (choice.equals(value)) return true;
        return false;
    }

    private static int duration(ActivityCategory category) {
        switch (category) {
            case COFFEE: return 30;
            case FOOD: case SHOPPING: return 60;
            case MUSEUM: case ENTERTAINMENT: return 120;
            default: return 90;
        }
    }

    private static LocalTime[] hours(String openingHours) {
        LocalTime defaultOpen = LocalTime.of(9, 0), defaultClose = LocalTime.of(21, 0);
        if (blank(openingHours)) return new LocalTime[] {defaultOpen, defaultClose};
        if (openingHours.toLowerCase().contains("24/7"))
            return new LocalTime[] {LocalTime.MIDNIGHT, LocalTime.of(23, 59)};
        Matcher matcher = HOURS.matcher(openingHours);
        LocalTime earliest = null, latest = null;
        while (matcher.find()) {
            try {
                LocalTime start = LocalTime.of(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)));
                LocalTime end = LocalTime.of(Integer.parseInt(matcher.group(3)),
                        Integer.parseInt(matcher.group(4)));
                if (start.isAfter(end)) continue;
                if (earliest == null || start.isBefore(earliest)) earliest = start;
                if (latest == null || end.isAfter(latest)) latest = end;
            } catch (RuntimeException ignored) { }
        }
        return earliest == null ? new LocalTime[] {defaultOpen, defaultClose}
                : new LocalTime[] {earliest, latest};
    }
}
