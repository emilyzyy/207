package closeai.adapters.viewmodels;

import java.time.LocalDate;

/** Immutable header and overview display state. */
public final class DashboardState {
    private final String destination;
    private final LocalDate date;
    private final String weatherCondition;
    private final String weatherMessage;

    public DashboardState(
            String destination, LocalDate date,
            String weatherCondition, String weatherMessage) {
        this.destination = destination == null ? "" : destination;
        this.date = date;
        this.weatherCondition = weatherCondition == null ? "" : weatherCondition;
        this.weatherMessage = weatherMessage == null ? "" : weatherMessage;
    }

    public String getDestination() {
        return destination;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getWeatherCondition() {
        return weatherCondition;
    }

    public String getWeatherMessage() {
        return weatherMessage;
    }
}
