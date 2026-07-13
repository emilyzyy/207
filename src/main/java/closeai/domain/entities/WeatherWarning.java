package closeai.domain.entities;

import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.WeatherSeverity;
import java.time.LocalTime;

public final class WeatherWarning {
    private final Location location;
    private final LocalTime time;
    private final String weatherCondition;
    private final WeatherSeverity severity;
    private final String message;

    public WeatherWarning(Location location, LocalTime time, String weatherCondition,
                          WeatherSeverity severity, String message) {
        this.location = location;
        this.time = time;
        this.weatherCondition = weatherCondition;
        this.severity = severity;
        this.message = message;
    }

    public Location getLocation() { return location; }
    public LocalTime getTime() { return time; }
    public String getWeatherCondition() { return weatherCondition; }
    public WeatherSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
}
