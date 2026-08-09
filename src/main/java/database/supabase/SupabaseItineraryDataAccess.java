package database.supabase;

import use_case.usecases.PlaceHydrator;
import use_case.ports.AuthService;
import use_case.ports.AuthSession;
import use_case.ports.ItineraryDataAccessInterface;
import use_case.ports.TripRepository;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.TripDay;
import entity.valueobjects.EventType;
import entity.valueobjects.TransportationMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists itineraries to Supabase PostgREST using lean place refs; hydrates activities on load.
 */
public final class SupabaseItineraryDataAccess
        implements TripRepository, ItineraryDataAccessInterface {
    private final String baseUrl;
    private final String anonKey;
    private final AuthService auth;
    private final PlaceHydrator hydrator;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public SupabaseItineraryDataAccess(
            String baseUrl, String anonKey, AuthService auth, PlaceHydrator hydrator) {
        this(baseUrl, anonKey, auth, hydrator,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                new ObjectMapper());
    }

    public SupabaseItineraryDataAccess(
            String baseUrl, String anonKey, AuthService auth, PlaceHydrator hydrator,
            HttpClient http, ObjectMapper mapper) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Supabase URL is required");
        }
        if (anonKey == null || anonKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Supabase anon key is required");
        }
        if (auth == null || hydrator == null) {
            throw new IllegalArgumentException("Auth and place hydrator are required");
        }
        this.baseUrl = trimSlash(baseUrl.trim());
        this.anonKey = anonKey.trim();
        this.auth = auth;
        this.hydrator = hydrator;
        this.http = http == null ? HttpClient.newHttpClient() : http;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @Override
    public Trip save(Trip trip) {
        return saveItinerary(trip);
    }

    @Override
    public Trip saveItinerary(Trip itinerary) {
        if (itinerary == null) {
            throw new IllegalArgumentException("Itinerary is required");
        }
        AuthSession session = requireSession();
        Optional<String> existingOwner = lookupTripOwner(itinerary.getId());
        ObjectNode tripRow = mapper.createObjectNode();
        tripRow.put("destination", itinerary.getDestination());
        tripRow.put("trip_date", itinerary.getDay(0).getDate().toString());
        tripRow.put("start_time", itinerary.getDay(0).getStartTime().toString());
        tripRow.put("end_time", itinerary.getDay(0).getEndTime().toString());
        tripRow.put("transportation_mode", itinerary.getTransportationMode().name());
        tripRow.put("created_at", java.time.Instant.now().toString());
        tripRow.put("updated_at", java.time.Instant.now().toString());

        if (existingOwner.isPresent()) {
            // PATCH omits user_id so collaborators cannot steal ownership, and avoids upsert RLS.
            request("PATCH", "/rest/v1/trips?id=eq." + enc(itinerary.getId()),
                    tripRow.toString(), "return=minimal");
        } else {
            tripRow.put("id", itinerary.getId());
            tripRow.put("user_id", session.getUserId());
            request("POST", "/rest/v1/trips", tripRow.toString(), "return=minimal");
        }

        request("DELETE", "/rest/v1/trip_days?trip_id=eq." + enc(itinerary.getId()),
                null, null);
        ArrayNode days = mapper.createArrayNode();
        for (int i = 0; i < itinerary.getDayCount(); i++) {
            days.add(dayRow(itinerary.getId(), i, itinerary.getDay(i)));
        }
        if (days.size() > 0) {
            request("POST", "/rest/v1/trip_days", days.toString(), "return=minimal");
        }

        request("DELETE", "/rest/v1/trip_bookmarks?trip_id=eq." + enc(itinerary.getId()),
                null, null);
        ArrayNode bookmarks = mapper.createArrayNode();
        for (Activity activity : itinerary.getBookmarkedActivities()) {
            bookmarks.add(bookmarkRow(itinerary.getId(), activity));
        }
        if (bookmarks.size() > 0) {
            request("POST", "/rest/v1/trip_bookmarks", bookmarks.toString(), "return=minimal");
        }

        request("DELETE", "/rest/v1/scheduled_events?trip_id=eq." + enc(itinerary.getId()),
                null, null);
        ArrayNode events = mapper.createArrayNode();
        for (int dayIndex = 0; dayIndex < itinerary.getDayCount(); dayIndex++) {
            int order = 0;
            for (ScheduledEvent event : itinerary.getDay(dayIndex).getScheduledEvents()) {
                events.add(eventRow(itinerary.getId(), event, dayIndex, order++));
            }
        }
        if (events.size() > 0) {
            request("POST", "/rest/v1/scheduled_events", events.toString(), "return=minimal");
        }
        return itinerary;
    }

    @Override
    public Optional<Trip> findById(String id) {
        return loadItinerary(id);
    }

    @Override
    public Optional<Trip> loadItinerary(String itineraryId) {
        if (itineraryId == null || itineraryId.trim().isEmpty()) {
            return Optional.empty();
        }
        requireSession();
        String id = itineraryId.trim();
        String path = "/rest/v1/trips?id=eq." + enc(id);
        JsonNode rows = readArray(request("GET", path, null, null));
        if (rows == null || rows.size() == 0) {
            return Optional.empty();
        }
        ObjectNode row = (ObjectNode) rows.get(0);
        row.set("trip_bookmarks", children("trip_bookmarks", id));
        row.set("trip_days", children("trip_days", id));
        row.set("scheduled_events", children("scheduled_events", id));
        return Optional.of(toTrip(row));
    }

    private JsonNode children(String table, String tripId) {
        String path = "/rest/v1/" + table + "?trip_id=eq." + enc(tripId);
        return readArray(request("GET", path, null, null));
    }

    @Override
    public boolean existsById(String itineraryId) {
        return loadItinerary(itineraryId).isPresent();
    }

    @Override
    public List<Trip> findAll() {
        requireSession();
        String path = "/rest/v1/trips?order=updated_at.desc";
        JsonNode rows = readArray(request("GET", path, null, null));
        List<Trip> trips = new ArrayList<Trip>();
        if (rows == null || rows.size() == 0) {
            return trips;
        }
        StringBuilder ids = new StringBuilder();
        for (JsonNode row : rows) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(text(row, "id"));
        }
        ObjectNode tripRows = mapper.createObjectNode();
        tripRows.set("trip_bookmarks", childrenIn("trip_bookmarks", ids.toString()));
        tripRows.set("trip_days", childrenIn("trip_days", ids.toString()));
        tripRows.set("scheduled_events", childrenIn("scheduled_events", ids.toString()));
        Map<String, JsonNode> bookmarks = indexByTrip(tripRows.get("trip_bookmarks"));
        Map<String, JsonNode> days = indexByTrip(tripRows.get("trip_days"));
        Map<String, JsonNode> events = indexByTrip(tripRows.get("scheduled_events"));
        for (JsonNode row : rows) {
            String id = text(row, "id");
            ObjectNode merged = (ObjectNode) row;
            merged.set("trip_bookmarks", bookmarks.getOrDefault(id, mapper.createArrayNode()));
            merged.set("trip_days", days.getOrDefault(id, mapper.createArrayNode()));
            merged.set("scheduled_events", events.getOrDefault(id, mapper.createArrayNode()));
            trips.add(toTrip(merged));
        }
        return trips;
    }

    @Override
    public boolean deleteById(String id) {
        if (id == null || id.trim().isEmpty()) return false;
        requireSession();
        String encoded = enc(id.trim());
        request("DELETE", "/rest/v1/scheduled_events?trip_id=eq." + encoded, null, null);
        request("DELETE", "/rest/v1/trip_bookmarks?trip_id=eq." + encoded, null, null);
        request("DELETE", "/rest/v1/trip_days?trip_id=eq." + encoded, null, null);
        request("DELETE", "/rest/v1/trips?id=eq." + encoded, null, null);
        return true;
    }

    private JsonNode childrenIn(String table, String tripIds) {
        String path = "/rest/v1/" + table + "?trip_id=in.(" + tripIds + ")";
        return readArray(request("GET", path, null, null));
    }

    private Map<String, JsonNode> indexByTrip(JsonNode children) {
        Map<String, JsonNode> byTrip = new java.util.HashMap<>();
        if (children == null || !children.isArray()) {
            return byTrip;
        }
        for (JsonNode child : children) {
            String tripId = text(child, "trip_id");
            JsonNode list = byTrip.get(tripId);
            if (list == null || !list.isArray()) {
                list = mapper.createArrayNode();
                byTrip.put(tripId, list);
            }
            ((ArrayNode) list).add(child);
        }
        return byTrip;
    }

    private Trip toTrip(JsonNode row) {
        String id = text(row, "id");
        String destination = text(row, "destination");
        LocalDate date = LocalDate.parse(text(row, "trip_date"));
        LocalTime start = LocalTime.parse(normalizeTime(text(row, "start_time")));
        LocalTime end = LocalTime.parse(normalizeTime(text(row, "end_time")));
        TransportationMode mode = TransportationMode.valueOf(text(row, "transportation_mode"));
        Trip trip = new Trip(id, destination, mode, loadDays(row, date, start, end));

        JsonNode bookmarks = row.get("trip_bookmarks");
        if (bookmarks != null && bookmarks.isArray()) {
            for (JsonNode bookmark : bookmarks) {
                Activity activity = hydrator.hydrate(
                        text(bookmark, "place_id"),
                        text(bookmark, "name"),
                        bookmark.path("latitude").asDouble(),
                        bookmark.path("longitude").asDouble(),
                        destination);
                trip.bookmark(activity);
            }
        }

        JsonNode events = row.get("scheduled_events");
        if (events != null && events.isArray()) {
            List<JsonNode> ordered = new ArrayList<JsonNode>();
            for (JsonNode event : events) {
                ordered.add(event);
            }
            ordered.sort(Comparator.comparingInt(node -> node.path("sort_order").asInt(0)));
            for (JsonNode event : ordered) {
                int dayIndex = event.path("sort_order").asInt(0) / 100_000;
                if (dayIndex < 0 || dayIndex >= trip.getDayCount()) {
                    dayIndex = 0;
                }
                EventType type = EventType.valueOf(text(event, "event_type"));
                LocalTime eventStart = LocalTime.parse(normalizeTime(text(event, "start_time")));
                LocalTime eventEnd = LocalTime.parse(normalizeTime(text(event, "end_time")));
                String notes = text(event, "notes");
                if (notes == null) {
                    notes = "";
                }
                Activity activity = null;
                if (type == EventType.ACTIVITY) {
                    activity = hydrator.hydrate(
                            text(event, "place_id"),
                            text(event, "name"),
                            event.path("latitude").asDouble(),
                            event.path("longitude").asDouble(),
                            destination);
                }
                trip.getDay(dayIndex).addEvent(new ScheduledEvent(
                        text(event, "id"), activity, eventStart, eventEnd, type, notes));
            }
        }
        return trip;
    }

    private List<TripDay> loadDays(JsonNode row, LocalDate fallbackDate,
                                   LocalTime fallbackStart, LocalTime fallbackEnd) {
        List<TripDay> days = new ArrayList<TripDay>();
        JsonNode dayRows = row.get("trip_days");
        if (dayRows == null || !dayRows.isArray() || dayRows.size() == 0) {
            days.add(new TripDay(fallbackDate, fallbackStart, fallbackEnd));
            return days;
        }
        List<JsonNode> ordered = new ArrayList<JsonNode>();
        for (JsonNode day : dayRows) {
            ordered.add(day);
        }
        ordered.sort(Comparator.comparingInt(node -> node.path("day_index").asInt(0)));
        for (JsonNode day : ordered) {
            days.add(new TripDay(
                    LocalDate.parse(text(day, "trip_date")),
                    LocalTime.parse(normalizeTime(text(day, "start_time"))),
                    LocalTime.parse(normalizeTime(text(day, "end_time")))));
        }
        return days;
    }

    private ObjectNode dayRow(String tripId, int dayIndex, TripDay day) {
        ObjectNode row = mapper.createObjectNode();
        row.put("trip_id", tripId);
        row.put("day_index", dayIndex);
        row.put("trip_date", day.getDate().toString());
        row.put("start_time", day.getStartTime().toString());
        row.put("end_time", day.getEndTime().toString());
        return row;
    }

    private ObjectNode bookmarkRow(String tripId, Activity activity) {
        ObjectNode row = mapper.createObjectNode();
        row.put("trip_id", tripId);
        row.put("place_id", activity.getId());
        row.put("name", activity.getName());
        row.put("latitude", activity.getLocation().getLatitude());
        row.put("longitude", activity.getLocation().getLongitude());
        return row;
    }

    private ObjectNode eventRow(String tripId, ScheduledEvent event, int dayIndex, int sortOrder) {
        ObjectNode row = mapper.createObjectNode();
        row.put("id", event.getId());
        row.put("trip_id", tripId);
        row.put("event_type", event.getEventType().name());
        row.put("start_time", event.getStartTime().toString());
        row.put("end_time", event.getEndTime().toString());
        row.put("notes", event.getNotes() == null ? "" : event.getNotes());
        row.put("sort_order", dayIndex * 100_000 + sortOrder);
        if (event.getEventType() == EventType.ACTIVITY && event.getActivity() != null) {
            Activity activity = event.getActivity();
            row.put("place_id", activity.getId());
            row.put("name", activity.getName());
            row.put("latitude", activity.getLocation().getLatitude());
            row.put("longitude", activity.getLocation().getLongitude());
        } else {
            row.putNull("place_id");
            row.putNull("name");
            row.putNull("latitude");
            row.putNull("longitude");
        }
        return row;
    }

    private AuthSession requireSession() {
        return auth.currentSession().orElseThrow(() ->
                new IllegalStateException("Sign in before saving or loading itineraries"));
    }

    private Optional<String> lookupTripOwner(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            String body = request("GET",
                    "/rest/v1/trips?id=eq." + enc(tripId) + "&select=user_id&limit=1",
                    null, null);
            JsonNode array = readArray(body);
            if (!array.isArray() || array.size() == 0) {
                return Optional.empty();
            }
            String owner = text(array.get(0), "user_id");
            return owner == null || owner.isEmpty() ? Optional.empty() : Optional.of(owner);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String request(String method, String path, String jsonBody, String prefer) {
        try {
            AuthSession session = requireSession();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json");
            if (prefer != null && !prefer.isEmpty()) {
                builder.header("Prefer", prefer);
            }
            if ("GET".equals(method)) {
                builder.GET();
            } else if ("DELETE".equals(method)) {
                builder.DELETE();
            } else if ("POST".equals(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            } else if ("PATCH".equals(method)) {
                builder.method("PATCH",
                        HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            } else {
                throw new IllegalArgumentException("Unsupported method: " + method);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Supabase HTTP " + response.statusCode()
                        + " " + method + " " + path + ": " + truncate(response.body()));
            }
            return response.body() == null ? "" : response.body();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Supabase request failed: " + exception.getMessage(),
                    exception);
        }
    }

    private JsonNode readArray(String body) {
        try {
            if (body == null || body.trim().isEmpty()) {
                return mapper.createArrayNode();
            }
            return mapper.readTree(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid Supabase JSON: " + exception.getMessage(),
                    exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String normalizeTime(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Time is required");
        }
        // Postgres time may arrive as HH:mm:ss or HH:mm:ss.ffffff
        String trimmed = value.trim();
        int dot = trimmed.indexOf('.');
        if (dot > 0) {
            trimmed = trimmed.substring(0, dot);
        }
        return trimmed;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }
}
