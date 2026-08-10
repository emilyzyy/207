package interface_adapter.weather;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
final class OpenMeteoForecastResponse {
    public Hourly hourly;

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Hourly {
        public List<String> time;

        @JsonProperty("weather_code")
        public List<Integer> weatherCode;

        @JsonProperty("temperature_2m")
        public List<Double> temperature;

        @JsonProperty("precipitation_probability")
        public List<Integer> precipitationProbability;

        @JsonProperty("wind_speed_10m")
        public List<Double> windSpeed;
    }
}
