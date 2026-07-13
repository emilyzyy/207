package closeai.infrastructure.weather;

/** Signals that an external weather lookup could not produce a usable forecast. */
public final class WeatherServiceException extends RuntimeException {
    public WeatherServiceException(String message) {
        super(message);
    }

    public WeatherServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
