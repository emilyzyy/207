package interface_adapter.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import entity.entities.Activity;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import interface_adapter.presenters.JsonPresenter;
import use_case.ports.ApiTripService;
import use_case.ports.PlacesWriter;
import use_case.usecases.CreateTripInputData;
import use_case.usecases.EditItineraryInputData;

public final class ApiController implements HttpHandler {
    private final ApiTripService app;
    private final PlacesWriter cachedPlaces;
    private final JsonPresenter presenter = new JsonPresenter();
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiController(ApiTripService app) {
        this(app, app != null ? app.cachedPlaces() : null);
    }

    public ApiController(ApiTripService app, PlacesWriter cachedPlaces) {
        if (app == null) {
            throw new IllegalArgumentException("Application container is required");
        }
        this.app = app;
        this.cachedPlaces = cachedPlaces;
    }

    /**
     * Performs the h an dl e operation.
     * @param exchange the e xc ha ng e value
     * @throws IOException if the operation cannot be completed
     */
    public void handle(HttpExchange exchange) throws IOException {
        addHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        try {
            final String path = exchange.getRequestURI().getPath();
            final String method = exchange.getRequestMethod();
            final String[] parts = path.split("/");
            if ("GET".equals(method) && "/api/activities".equals(path)) {
                final String query = queryParam(exchange.getRequestURI().getRawQuery(), "query");
                respond(exchange, 200, presenter.activities(app.searchActivities("Toronto", query)));
                return;
            }
            if ("POST".equals(method) && "/api/places/search".equals(path)) {
                if (cachedPlaces == null) {
                    throw new IllegalArgumentException(
                            "Discovered-place storage is unavailable");
                }
                final List<Activity> places = parsePlacesFromJs(readBody(exchange));
                cachedPlaces.addAll(places);
                respond(exchange, 200, presenter.activities(places));
                return;
            }
            if ("POST".equals(method) && "/api/trips".equals(path)) {
                final JsonRequest request = new JsonRequest(readBody(exchange));
                final int dayCount = parseDayCount(request.get("dayCount", "1"));
                final Trip trip = app.createTrip(new CreateTripInputData(
                        request.get("destination", "Toronto"),
                        LocalDate.parse(request.get("date", "2026-07-18")),
                        LocalTime.parse(request.get("startTime", "09:00")),
                        LocalTime.parse(request.get("endTime", "19:00")),
                        TransportationMode.valueOf(
                                request.get("transportationMode", "WALKING")),
                        dayCount));
                respond(exchange, 201, presenter.trip(trip));
                return;
            }
            if (parts.length >= 4 && "trips".equals(parts[2])) {
                final String tripId = parts[3];
                if (parts.length == 4 && "GET".equals(method)) {
                    final Trip trip = app.findTrip(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
                    respond(exchange, 200, presenter.trip(trip));
                    return;
                }
                if (parts.length == 4 && "PUT".equals(method)) {
                    final JsonRequest request = new JsonRequest(readBody(exchange));
                    final Trip existing = app.findTrip(tripId)
                            .orElseThrow(() -> new IllegalArgumentException("Itinerary not found"));
                    final Trip trip = app.editItinerary(new EditItineraryInputData(
                            tripId,
                            request.get("destination", existing.getDestination()),
                            LocalDate.parse(request.get("date", existing.getDate().toString())),
                            LocalTime.parse(request.get("startTime", existing.getStartTime().toString())),
                            LocalTime.parse(request.get("endTime", existing.getEndTime().toString())),
                            TransportationMode.valueOf(request.get("transportationMode",
                                    existing.getTransportationMode().name()))));
                    respond(exchange, 200, presenter.trip(trip));
                    return;
                }
                if (parts.length == 6 && "bookmarks".equals(parts[4])) {
                    final Trip trip = "POST".equals(method) ? app.bookmarkActivity(tripId, parts[5])
                            : app.removeBookmark(tripId, parts[5]);
                    respond(exchange, 200, presenter.trip(trip));
                    return;
                }
                if (parts.length == 6 && "plan".equals(parts[4]) && "manual".equals(parts[5]) && "POST".equals(method)) {
                    final JsonRequest request = new JsonRequest(readBody(exchange));
                    final Trip trip = app.addActivityToPlan(tripId, request.get("activityId", "rom"),
                            optionalTime(request.get("startTime", "")));
                    respond(exchange, 200, presenter.trip(trip));
                    return;
                }
                if (parts.length == 6 && "plan".equals(parts[4]) && "autoschedule".equals(parts[5]) && "POST".equals(method)) {
                    respond(exchange, 200, presenter.trip(app.autoSchedule(tripId)));
                    return;
                }
                if (parts.length == 6 && "plan".equals(parts[4]) && "DELETE".equals(method)) {
                    respond(exchange, 200, presenter.trip(app.removeEvent(tripId, parts[5])));
                    return;
                }
                if (parts.length == 6 && "plan".equals(parts[4]) && "PUT".equals(method)) {
                    final JsonRequest request = new JsonRequest(readBody(exchange));
                    final Trip trip = app.editEvent(tripId, parts[5],
                            LocalTime.parse(request.get("startTime", "10:00")),
                            LocalTime.parse(request.get("endTime", "11:00")), request.get("notes", "Edited"));
                    respond(exchange, 200, presenter.trip(trip));
                    return;
                }
                if (parts.length == 5 && "summary".equals(parts[4]) && "GET".equals(method)) {
                    respond(exchange, 200, presenter.message(app.tripSummary(tripId)));
                    return;
                }
                if (parts.length == 5 && "share".equals(parts[4]) && "GET".equals(method)) {
                    respond(exchange, 200, presenter.message(app.shareTrip(tripId)));
                    return;
                }
                if (parts.length == 5 && "weather".equals(parts[4]) && "GET".equals(method)) {
                    respond(exchange, 200, presenter.weather(app.weatherWarning(tripId)));
                    return;
                }
                if (parts.length == 6 && "weather".equals(parts[4])
                        && "hourly".equals(parts[5]) && "GET".equals(method)) {
                    respond(exchange, 200,
                            presenter.hourlyWeather(app.hourlyWeather(tripId)));
                            return;
                }
            }
            respond(exchange, 404, presenter.error("Route not found"));
        }
        catch (IllegalArgumentException exception) {
            respond(exchange, 400, presenter.error(exception.getMessage()));
        }
        catch (Exception exception) {
            respond(exchange, 500, presenter.error("Unexpected server error"));
        }
    }

    private static LocalTime optionalTime(String value) {
        return value == null || value.isEmpty() ? null : LocalTime.parse(value);
    }

    private static int parseDayCount(String value) {
        try {
            final int count = Integer.parseInt(value);
            return count < 1 ? 1 : count;
        }
        catch (NumberFormatException exception) {
            return 1;
        }
    }

    private List<Activity> parsePlacesFromJs(String json) {
        try {
            final JsonNode root = mapper.readTree(json);
            final JsonNode placesNode = root.path("places");
            if (!placesNode.isArray()) {
                return new ArrayList<>();
            }
            final List<Activity> result = new ArrayList<>();
            for (JsonNode place : placesNode) {
                final String id = place.path("id").asText(null);
                final String name = place.path("name").asText(null);
                if (id == null || name == null) {
                    continue;
                }
                final double lat = place.path("latitude").asDouble(0);
                final double lng = place.path("longitude").asDouble(0);
                final String address = place.path("address").asText("");
                final double rating = place.path("rating").asDouble(0);
                final String categoryStr = place.path("category").asText("ATTRACTION");
                final String typeStr = place.path("type").asText("MIXED");
                final int duration = place.path("durationMinutes").asInt(60);
                ActivityCategory category;
                try {
                    category = ActivityCategory.valueOf(categoryStr);
                }
                catch (IllegalArgumentException e) {
                    category = ActivityCategory.ATTRACTION;
                }
                IndoorOutdoorType indoorType;
                try {
                    indoorType = IndoorOutdoorType.valueOf(typeStr);
                }
                catch (IllegalArgumentException e) {
                    indoorType = IndoorOutdoorType.MIXED;
                }
                final String risk = indoorType == IndoorOutdoorType.OUTDOOR ? "High"
                        : indoorType == IndoorOutdoorType.MIXED ? "Medium" : "Low";
                result.add(new Activity(id, name, category, new Location(lat, lng, address),
                        rating, duration, LocalTime.of(9, 0), LocalTime.of(21, 0), indoorType, risk));
            }
            return result;
        }
        catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        final InputStream input = exchange.getRequestBody();
        final byte[] bytes = input.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String queryParam(String query, String key) {
        if (query == null) {
            return "";
        }
        for (String pair : query.split("&")) {
            final String[] item = pair.split("=", 2);
            if (item[0].equals(key)) {
                return URLDecoder.decode(item.length > 1 ? item[1] : "", StandardCharsets.UTF_8);
            }
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
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        final OutputStream output = exchange.getResponseBody();
        output.write(bytes);
        output.close();
    }
}
