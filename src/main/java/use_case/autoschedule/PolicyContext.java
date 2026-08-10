package use_case.autoschedule;

/** Everything a soft policy may consult beyond the placement itself. */
public final class PolicyContext {
    private static final PolicyContext EMPTY = new PolicyContext(WeatherContext.unavailable());

    private final WeatherContext weather;

    public PolicyContext(WeatherContext weather) {
        this.weather = weather == null ? WeatherContext.unavailable() : weather;
    }

    /**
     * Performs the e mp ty operation.
     * @return the result of the operation
     */
    public static PolicyContext empty() {
        return EMPTY;
    }

    public WeatherContext getWeather() {
        return weather;
    }
}
