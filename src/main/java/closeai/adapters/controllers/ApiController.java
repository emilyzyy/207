package closeai.adapters.controllers;

import closeai.adapters.presenters.JsonPresenter;
import closeai.application.AppContainer;
import closeai.application.usecases.EditItineraryInputData;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;

public final class ApiController implements HttpHandler {
    private final AppContainer app;
    private final JsonPresenter presenter = new JsonPresenter();
    public ApiController(AppContainer app) { this.app = app; }

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
            if ("POST".equals(method) && "/api/trips".equals(path)) {
                JsonRequest request = new JsonRequest(readBody(exchange));
                Trip trip = app.createTrip.execute(request.get("destination", "Toronto"),
                        LocalDate.parse(request.get("date", "2026-07-18")),
                        LocalTime.parse(request.get("startTime", "09:00")),
                        LocalTime.parse(request.get("endTime", "19:00")),
                        TransportationMode.valueOf(request.get("transportationMode", "WALKING")));
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
            }
            respond(exchange, 404, presenter.error("Route not found"));
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, presenter.error(exception.getMessage()));
        } catch (Exception exception) {
            respond(exchange, 500, presenter.error("Unexpected server error"));
        }
    }

    private static LocalTime optionalTime(String value) { return value == null || value.isEmpty() ? null : LocalTime.parse(value); }
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
