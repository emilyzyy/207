package closeai.adapters.controllers;

import closeai.adapters.presenters.JsonPresenter;
import closeai.application.AppContainer;
import closeai.application.usecases.CreateTripInputData;
import closeai.application.usecases.EditItineraryInputData;
import closeai.domain.entities.Activity;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import closeai.application.ports.PlacesWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class ApiController implements HttpHandler {
    private final AppContainer app;
    private final PlacesWriter cachedPlaces;
    private final JsonPresenter presenter = new JsonPresenter();
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiController(AppContainer app) {
        this(
                app,
                app != null && app.activities instanceof PlacesWriter
                        ? (PlacesWriter) app.activities : null);
    }

    public ApiController(AppContainer app, PlacesWriter cachedPlaces) {
        if (app == null) {
            throw new IllegalArgumentException("Application container is required");
        }
        this.app = app;
        this.cachedPlaces = cachedPlaces;
    }

    public void handle(HttpExchange exchange) throws IOException {
        addHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String[] parts = path.split("/");
            if ("GET".equals(method) && "/api/activities".equals(path)) {
                String query = queryParam(exchange.getRequestURI().getRawQuery(), "query");
                respond(exchange, 200, presenter.activities(app.searchActivities.execute("Toronto", query))); return;
            }
            if ("POST".equals(method) && "/api/places/search".equals(path)) {
                if (cachedPlaces == null) {
                    throw new IllegalArgumentException(
                            "Discovered-place storage is unavailable");
                }
                List<Activity> places = parsePlacesFromJs(readBody(exchange));
                cachedPlaces.addAll(places);
                respond(exchange, 200, presenter.activities(places)); return;
            }
            if ("POST".equals(method) && "/api/trips".equals(path)) {
                JsonRequest request = new JsonRequest(readBody(exchange));
                int dayCount = parseDayCount(request.get("dayCount", "1"));
                Trip trip = app.createTrip.execute(new CreateTripInputData(
                        request.get("destination", "Toronto"),
                        LocalDate.parse(request.get("date", "2026-07-18")),
                        LocalTime.parse(request.get("startTime", "09:00")),
                        LocalTime.parse(request.get("endTime", "19:00")),
                        TransportationMode.valueOf(
                                request.get("transportationMode", "WALKING")),
                        dayCount));
                respond(exchange, 201, presenter.trip(trip)); return;
            }
            if (parts.length >= 4 && "trips".equals(parts[2])) {
                String tripId = parts[3];
                if (parts.length == 4 && "GET".equals(method)) {
                    Trip trip = app.trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
                    respond(exchange, 200, presenter.trip(trip)); return;
                }
                if (parts.length == 4 && "PUT".equals(method)) {
                    JsonRequest request = new JsonRequest(readBody(exchange));
                    Trip existing = app.trips.findById(tripId)
                            .orElseThrow(() -> new IllegalArgumentException("Itinerary not found"));
                    Trip trip = app.editItinerary.execute(new EditItineraryInputData(
                            tripId,
                            request.get("destination", existing.getDestination()),
                            LocalDate.parse(request.get("date", existing.getDate().toString())),
                            LocalTime.parse(request.get("startTime", existing.getStartTime().toString())),
                            LocalTime.parse(request.get("endTime", existing.getEndTime().toString())),
                            TransportationMode.valueOf(request.get("transportationMode",
                                    existing.getTransportationMode().name()))));
                    respond(exchange, 200, presenter.trip(trip)); return;
                }
                if (parts.length == 6 && "bookmarks".equals(parts[4])) {
                    Trip trip = "POST".equals(method) ? app.bookmarkActivity.execute(tripId, parts[5])
                            : app.removeBookmark.execute(tripId, parts[5]);
                    respond(exchange, 200, presenter.trip(trip)); return;
                }
                if (parts.length == 6 && "plan".equals(parts[4]) && "manual".equals(parts[5]) && "POST".equals(method)) {
                    JsonRequest request = new JsonRequest(readBody(exchange));
                    Trip trip = app.addActivityToPlan.execute(tripId, request.get("activityId", "rom"),
                            optionalTime(request.get("startTime", "")));
                    respond(exchange, 200, presenter.trip(trip)); return;
                }
                if (parts.length == 6 && "plan".equals(parts[4]) && "autoschedule".equals(parts[5]) && "POST".equals(method)) {
                    respond(exchange, 200, presenter.trip(app.autoSchedule.execute(tripId))); return;
                }
                if (parts.length == 6 && "plan".equals(parts[4]) && "DELETE".equals(method)) {
                    respond(exchange, 200, presenter.trip(app.removeEvent.execute(tripId, parts[5]))); return;
                }
                if (parts.length == 6 && "plan".equals(parts[4]) && "PUT".equals(method)) {
                    JsonRequest request = new JsonRequest(readBody(exchange));
                    Trip trip = app.editEvent.execute(tripId, parts[5],
                            LocalTime.parse(request.get("startTime", "10:00")),
                            LocalTime.parse(request.get("endTime", "11:00")), request.get("notes", "Edited"));
                    respond(exchange, 200, presenter.trip(trip)); return;
                }
                if (parts.length == 5 && "summary".equals(parts[4]) && "GET".equals(method)) {
                    respond(exchange, 200, presenter.message(app.summary.execute(tripId))); return;
                }
                if (parts.length == 5 && "share".equals(parts[4]) && "GET".equals(method)) {
                    respond(exchange, 200, presenter.message(app.share.execute(tripId))); return;
                }
                if (parts.length == 5 && "weather".equals(parts[4]) && "GET".equals(method)) {
                    respond(exchange, 200, presenter.weather(app.weatherWarning.execute(tripId))); return;
                }
                if (parts.length == 6 && "weather".equals(parts[4])
                        && "hourly".equals(parts[5]) && "GET".equals(method)) {
                    respond(exchange, 200,
                            presenter.hourlyWeather(app.weatherWarning.executeHourly(tripId))); return;
                }
            }
            respond(exchange, 404, presenter.error("Route not found"));
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, presenter.error(exception.getMessage()));
        } catch (Exception exception) {
            respond(exchange, 500, presenter.error("Unexpected server error"));
        }
    }

    private static LocalTime optionalTime(String value) { return value == null || value.isEmpty() ? null : LocalTime.parse(value); }

    private static int parseDayCount(String value) {
        try {
            int count = Integer.parseInt(value);
            return count < 1 ? 1 : count;
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private List<Activity> parsePlacesFromJs(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode placesNode = root.path("places");
            if (!placesNode.isArray()) return new ArrayList<>();
            List<Activity> result = new ArrayList<>();
            for (JsonNode place : placesNode) {
                String id = place.path("id").asText(null);
                String name = place.path("name").asText(null);
                if (id == null || name == null) continue;
                double lat = place.path("latitude").asDouble(0);
                double lng = place.path("longitude").asDouble(0);
                String address = place.path("address").asText("");
                double rating = place.path("rating").asDouble(0);
                String categoryStr = place.path("category").asText("ATTRACTION");
                String typeStr = place.path("type").asText("MIXED");
                int duration = place.path("durationMinutes").asInt(60);
                ActivityCategory category;
                try { category = ActivityCategory.valueOf(categoryStr); }
                catch (IllegalArgumentException e) { category = ActivityCategory.ATTRACTION; }
                IndoorOutdoorType indoorType;
                try { indoorType = IndoorOutdoorType.valueOf(typeStr); }
                catch (IllegalArgumentException e) { indoorType = IndoorOutdoorType.MIXED; }
                String risk = indoorType == IndoorOutdoorType.OUTDOOR ? "High"
                        : indoorType == IndoorOutdoorType.MIXED ? "Medium" : "Low";
                result.add(new Activity(id, name, category, new Location(lat, lng, address),
                        rating, duration, LocalTime.of(9, 0), LocalTime.of(21, 0), indoorType, risk));
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream input = exchange.getRequestBody();
        byte[] bytes = input.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static String queryParam(String query, String key) {
        if (query == null) return "";
        for (String pair : query.split("&")) {
            String[] item = pair.split("=", 2);
            if (item[0].equals(key)) return URLDecoder.decode(item.length > 1 ? item[1] : "", StandardCharsets.UTF_8);
        }
        return "";
    }
    private static void addHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream output = exchange.getResponseBody();
        output.write(bytes);
        output.close();
    }
}
