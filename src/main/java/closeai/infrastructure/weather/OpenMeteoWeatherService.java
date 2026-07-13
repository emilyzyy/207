package closeai.infrastructure.weather;

import closeai.application.ports.WeatherService;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.WeatherSeverity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/** WeatherService adapter backed by Open-Meteo's key-free geocoding and forecast APIs. */
public final class OpenMeteoWeatherService implements WeatherService {
    private static final URI GEOCODING_ENDPOINT =
            URI.create("https://geocoding-api.open-meteo.com/v1/search");
    private static final URI FORECAST_ENDPOINT =
            URI.create("https://api.open-meteo.com/v1/forecast");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient client;
    private final URI geocodingEndpoint;
    private final URI forecastEndpoint;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;

    public OpenMeteoWeatherService() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                GEOCODING_ENDPOINT, FORECAST_ENDPOINT, new ObjectMapper(), REQUEST_TIMEOUT);
    }

    OpenMeteoWeatherService(HttpClient client, URI geocodingEndpoint, URI forecastEndpoint,
                            ObjectMapper mapper, Duration requestTimeout) {
        if (client == null || geocodingEndpoint == null || forecastEndpoint == null
                || mapper == null || requestTimeout == null || requestTimeout.isZero()
                || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Valid Open-Meteo adapter dependencies are required");
        }
        this.client = client;
        this.geocodingEndpoint = geocodingEndpoint;
        this.forecastEndpoint = forecastEndpoint;
        this.mapper = mapper;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public WeatherWarning getWarning(Trip trip) {
        if (trip == null) throw new IllegalArgumentException("Trip is required");
        OpenMeteoGeocodingResponse.Result place = geocode(trip.getDestination());
        Location location = new Location(place.latitude, place.longitude, displayName(place));
        OpenMeteoForecastResponse forecast = fetchForecast(location, trip);
        ForecastPoint point = selectForecastPoint(forecast, trip);
        WeatherSeverity severity = severity(point.weatherCode,
                point.precipitationProbability, point.windSpeed);
        String condition = condition(point.weatherCode);
        String message = String.format(Locale.ROOT,
                "%.1f°C · %d%% precipitation · %.1f km/h wind · %s conditions.",
                point.temperature, point.precipitationProbability, point.windSpeed,
                severity.name().toLowerCase(Locale.ROOT));
        return new WeatherWarning(location, trip.getStartTime(), condition, severity, message);
    }

    private OpenMeteoGeocodingResponse.Result geocode(String destination) {
        String query = "name=" + encode(destination) + "&count=1&language=en&format=json";
        OpenMeteoGeocodingResponse response = getJson(withQuery(geocodingEndpoint, query),
                OpenMeteoGeocodingResponse.class, "geocoding");
        if (response.results == null || response.results.isEmpty()) {
            throw new WeatherServiceException("Open-Meteo found no location for destination: " + destination);
        }
        OpenMeteoGeocodingResponse.Result result = response.results.get(0);
        if (result == null || result.name == null || result.latitude == null || result.longitude == null
                || !Double.isFinite(result.latitude) || !Double.isFinite(result.longitude)) {
            throw new WeatherServiceException("Open-Meteo returned an invalid geocoding result");
        }
        return result;
    }

    private OpenMeteoForecastResponse fetchForecast(Location location, Trip trip) {
        String date = trip.getDate().toString();
        String query = "latitude=" + location.getLatitude()
                + "&longitude=" + location.getLongitude()
                + "&hourly=weather_code,temperature_2m,precipitation_probability,wind_speed_10m"
                + "&timezone=auto&start_date=" + date + "&end_date=" + date;
        return getJson(withQuery(forecastEndpoint, query), OpenMeteoForecastResponse.class, "forecast");
    }

    private <T> T getJson(URI uri, Class<T> responseType, String operation) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "CloseAI-CSC207/1.0")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WeatherServiceException("Open-Meteo " + operation
                        + " request failed with HTTP " + response.statusCode());
            }
            try {
                return mapper.readValue(response.body(), responseType);
            } catch (JsonProcessingException exception) {
                throw new WeatherServiceException("Open-Meteo " + operation
                        + " response contained invalid JSON", exception);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WeatherServiceException("Open-Meteo " + operation + " request was interrupted", exception);
        } catch (IOException exception) {
            throw new WeatherServiceException("Open-Meteo " + operation + " request failed", exception);
        }
    }

    private ForecastPoint selectForecastPoint(OpenMeteoForecastResponse response, Trip trip) {
        if (response == null || response.hourly == null || response.hourly.time == null
                || response.hourly.time.isEmpty()) {
            throw new WeatherServiceException("Open-Meteo forecast contained no hourly results");
        }
        OpenMeteoForecastResponse.Hourly hourly = response.hourly;
        requireAligned(hourly.weatherCode, hourly.time, "weather_code");
        requireAligned(hourly.temperature, hourly.time, "temperature_2m");
        requireAligned(hourly.precipitationProbability, hourly.time, "precipitation_probability");
        requireAligned(hourly.windSpeed, hourly.time, "wind_speed_10m");

        LocalDateTime requested = LocalDateTime.of(trip.getDate(), trip.getStartTime());
        int bestIndex = -1;
        long bestDifference = Long.MAX_VALUE;
        for (int i = 0; i < hourly.time.size(); i++) {
            try {
                LocalDateTime forecastTime = LocalDateTime.parse(hourly.time.get(i));
                if (!forecastTime.toLocalDate().equals(trip.getDate())) continue;
                long difference = Math.abs(Duration.between(requested, forecastTime).toMinutes());
                if (difference < bestDifference) {
                    bestDifference = difference;
                    bestIndex = i;
                }
            } catch (DateTimeParseException ignored) {
                // A malformed item is ignored if another valid hourly item can be selected.
            }
        }
        if (bestIndex < 0) {
            throw new WeatherServiceException("Open-Meteo forecast had no usable hour for the trip date");
        }
        Integer code = hourly.weatherCode.get(bestIndex);
        Double temperature = hourly.temperature.get(bestIndex);
        Integer precipitation = hourly.precipitationProbability.get(bestIndex);
        Double wind = hourly.windSpeed.get(bestIndex);
        if (code == null || temperature == null || precipitation == null || wind == null
                || !Double.isFinite(temperature) || !Double.isFinite(wind)) {
            throw new WeatherServiceException("Open-Meteo forecast contained incomplete hourly values");
        }
        return new ForecastPoint(code, temperature, Math.max(0, Math.min(100, precipitation)), wind);
    }

    private void requireAligned(List<?> values, List<String> times, String field) {
        if (values == null || values.size() != times.size()) {
            throw new WeatherServiceException("Open-Meteo forecast field " + field + " was missing or misaligned");
        }
    }

    private WeatherSeverity severity(int code, int precipitationProbability, double windSpeed) {
        if (code >= 95 || code == 65 || code == 67 || code == 75 || code == 77
                || code == 82 || code == 86 || precipitationProbability >= 80 || windSpeed >= 50.0) {
            return WeatherSeverity.HIGH;
        }
        if (code >= 45 || precipitationProbability >= 40 || windSpeed >= 30.0) {
            return WeatherSeverity.MEDIUM;
        }
        return WeatherSeverity.LOW;
    }

    private String condition(int code) {
        if (code == 0) return "Clear sky";
        if (code <= 3) return "Partly cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "Unknown conditions";
    }

    private String displayName(OpenMeteoGeocodingResponse.Result place) {
        StringBuilder name = new StringBuilder(place.name);
        if (place.admin1 != null && !place.admin1.trim().isEmpty()
                && !place.admin1.equalsIgnoreCase(place.name)) name.append(", ").append(place.admin1);
        if (place.country != null && !place.country.trim().isEmpty()) name.append(", ").append(place.country);
        return name.toString();
    }

    private URI withQuery(URI endpoint, String query) {
        return URI.create(endpoint.toString() + (endpoint.toString().contains("?") ? "&" : "?") + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class ForecastPoint {
        private final int weatherCode;
        private final double temperature;
        private final int precipitationProbability;
        private final double windSpeed;

        private ForecastPoint(int weatherCode, double temperature,
                              int precipitationProbability, double windSpeed) {
            this.weatherCode = weatherCode;
            this.temperature = temperature;
            this.precipitationProbability = precipitationProbability;
            this.windSpeed = windSpeed;
        }
    }
}
