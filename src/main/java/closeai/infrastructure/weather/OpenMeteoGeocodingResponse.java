package closeai.infrastructure.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
final class OpenMeteoGeocodingResponse {
    public List<Result> results;

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Result {
        public String name;
        public Double latitude;
        public Double longitude;
        public String country;
        public String admin1;
    }
}
