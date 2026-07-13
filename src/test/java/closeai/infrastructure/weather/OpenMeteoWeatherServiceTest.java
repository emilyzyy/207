package closeai.infrastructure.weather;

import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.TransportationMode;
import closeai.domain.valueobjects.WeatherSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OpenMeteoWeatherServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void mapsGeocodingAndForecastJsonWithoutLiveNetwork() throws Exception {
        AtomicReference<String> geocodingQuery = new AtomicReference<String>();
        AtomicReference<String> forecastQuery = new AtomicReference<String>();
        startServer();
        server.createContext("/geo", exchange -> {
            geocodingQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"results\":[{\"name\":\"Montréal\",\"latitude\":45.5019,"
                    + "\"longitude\":-73.5674,\"admin1\":\"Quebec\",\"country\":\"Canada\"}]}");
        });
        server.createContext("/forecast", exchange -> {
            forecastQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, forecastJson("2026-07-14T09:00", 95, 21.5, 90, 55.0));
        });
        server.start();

        WeatherWarning warning = service().getWarning(trip("Montréal, QC"));

        assertEquals(45.5019, warning.getLocation().getLatitude(), 0.00001);
        assertEquals("Montréal, Quebec, Canada", warning.getLocation().getAddress());
        assertEquals("Thunderstorm", warning.getWeatherCondition());
        assertEquals(WeatherSeverity.HIGH, warning.getSeverity());
        assertTrue(warning.getMessage().contains("21.5°C"));
        assertTrue(geocodingQuery.get().contains("name=Montr%C3%A9al%2C+QC"));
        assertTrue(forecastQuery.get().contains("start_date=2026-07-14"));
        assertTrue(forecastQuery.get().contains("weather_code"));
    }

    @Test
    void selectsNearestHourAndMapsLowSeverity() throws Exception {
        startServer();
        server.createContext("/geo", exchange -> respond(exchange, 200,
                "{\"results\":[{\"name\":\"Montreal\",\"latitude\":45.5,\"longitude\":-73.5}]}"));
        server.createContext("/forecast", exchange -> respond(exchange, 200,
                "{\"hourly\":{\"time\":[\"2026-07-14T08:00\",\"2026-07-14T10:00\"],"
                        + "\"weather_code\":[0,3],\"temperature_2m\":[18.0,20.0],"
                        + "\"precipitation_probability\":[0,10],\"wind_speed_10m\":[5.0,8.0]}}"));
        server.start();

        WeatherWarning warning = service().getWarning(trip("Montreal"));

        assertEquals("Clear sky", warning.getWeatherCondition());
        assertEquals(WeatherSeverity.LOW, warning.getSeverity());
    }

    @Test
    void rejectsNonSuccessfulHttpStatus() throws Exception {
        startServer();
        server.createContext("/geo", exchange -> respond(exchange, 503, "{\"reason\":\"down\"}"));
        server.start();

        WeatherServiceException error = assertThrows(WeatherServiceException.class,
                () -> service().getWarning(trip("Montreal")));

        assertTrue(error.getMessage().contains("HTTP 503"));
    }

    @Test
    void rejectsNoGeocodingResults() throws Exception {
        startServer();
        server.createContext("/geo", exchange -> respond(exchange, 200, "{\"results\":[]}"));
        server.start();

        WeatherServiceException error = assertThrows(WeatherServiceException.class,
                () -> service().getWarning(trip("Not A Place")));

        assertTrue(error.getMessage().contains("no location"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        startServer();
        server.createContext("/geo", exchange -> respond(exchange, 200, "not-json"));
        server.start();

        WeatherServiceException error = assertThrows(WeatherServiceException.class,
                () -> service().getWarning(trip("Montreal")));

        assertTrue(error.getMessage().contains("invalid JSON"));
    }

    @Test
    void rejectsMissingOrMisalignedForecastData() throws Exception {
        startServer();
        server.createContext("/geo", exchange -> respond(exchange, 200,
                "{\"results\":[{\"name\":\"Montreal\",\"latitude\":45.5,\"longitude\":-73.5}]}"));
        server.createContext("/forecast", exchange -> respond(exchange, 200,
                "{\"hourly\":{\"time\":[\"2026-07-14T09:00\"],\"weather_code\":[],"
                        + "\"temperature_2m\":[20.0],\"precipitation_probability\":[10],"
                        + "\"wind_speed_10m\":[8.0]}}"));
        server.start();

        WeatherServiceException error = assertThrows(WeatherServiceException.class,
                () -> service().getWarning(trip("Montreal")));

        assertTrue(error.getMessage().contains("misaligned"));
    }

    @Test
    void wrapsConnectionFailureAsWeatherServiceError() throws Exception {
        startServer();
        int unusedPort = server.getAddress().getPort();
        server.stop(0);
        server = null;
        URI unavailable = URI.create("http://127.0.0.1:" + unusedPort + "/unavailable");
        OpenMeteoWeatherService service = new OpenMeteoWeatherService(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build(),
                unavailable, unavailable, new ObjectMapper(), Duration.ofMillis(500));

        WeatherServiceException error = assertThrows(WeatherServiceException.class,
                () -> service.getWarning(trip("Montreal")));

        assertTrue(error.getMessage().contains("request failed"));
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    }

    private OpenMeteoWeatherService service() {
        String base = "http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort();
        return new OpenMeteoWeatherService(HttpClient.newHttpClient(),
                URI.create(base + "/geo"), URI.create(base + "/forecast"),
                new ObjectMapper(), Duration.ofSeconds(2));
    }

    private Trip trip(String destination) {
        return new Trip("weather-test", destination, LocalDate.of(2026, 7, 14),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
    }

    private String forecastJson(String time, int code, double temperature, int precipitation, double wind) {
        return "{\"hourly\":{\"time\":[\"" + time + "\"],\"weather_code\":[" + code
                + "],\"temperature_2m\":[" + temperature + "],\"precipitation_probability\":["
                + precipitation + "],\"wind_speed_10m\":[" + wind + "]}}";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
