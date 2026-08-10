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
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import use_case.ports.CityCandidate;
import use_case.ports.CitySearchGeocoder;

/** Open-Meteo geocoding autocomplete for the trip-destination picker. */
public final class OpenMeteoCitySearch implements CitySearchGeocoder {
    private static final int HTTP_OK = 200;
    private static final String DEFAULT_ENDPOINT =
            "https://geocoding-api.open-meteo.com/v1/search";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private final HttpClient http;
    private final ObjectMapper json;
    private final URI endpoint;

    /**
     * Creates a city search backed by the public Open-Meteo geocoding endpoint.
     */
    public OpenMeteoCitySearch() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                new ObjectMapper(), URI.create(DEFAULT_ENDPOINT));
    }

    OpenMeteoCitySearch(HttpClient http, ObjectMapper json, URI endpoint) {
        if (http == null || json == null || endpoint == null) {
            throw new IllegalArgumentException(
                    "Open-Meteo city search dependencies are required");
        }
        this.http = http;
        this.json = json;
        this.endpoint = endpoint;
    }

    @Override
    public List<CityCandidate> search(String query, int limit) {
        final List<CityCandidate> candidates;
        if (query == null || query.trim().isEmpty()) {
            candidates = new ArrayList<>();
        }
        else {
            candidates = fetch(query.trim(), Math.max(1, limit));
        }
        return candidates;
    }

    private List<CityCandidate> fetch(String query, int limit) {
        final List<CityCandidate> candidates = new ArrayList<>();
        try {
            final String parameters = "?name="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&count=" + limit + "&language=en&format=json";
            final HttpRequest request = HttpRequest.newBuilder(
                    URI.create(endpoint + parameters))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Trippy-CSC207/1.0")
                    .GET().build();
            final HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == HTTP_OK) {
                final JsonNode results = json.readTree(response.body()).path("results");
                if (results.isArray()) {
                    for (JsonNode node : results) {
                        candidates.add(new CityCandidate(
                                node.path("name").asText(),
                                node.path("admin1").asText(),
                                node.path("country").asText(),
                                node.path("latitude").asDouble(),
                                node.path("longitude").asDouble()));
                    }
                }
            }
        }
        catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return candidates;
    }
}
