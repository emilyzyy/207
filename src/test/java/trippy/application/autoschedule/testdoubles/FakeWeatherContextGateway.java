package trippy.application.autoschedule.testdoubles;

import trippy.application.autoschedule.WeatherContext;
import trippy.application.autoschedule.WeatherContextGateway;
import trippy.domain.entities.Trip;

/** Returns a fixed forecast context, or throws to exercise degradation. */
public final class FakeWeatherContextGateway implements WeatherContextGateway {

    private WeatherContext context = WeatherContext.unavailable();
    private boolean throwOnCall;

    public FakeWeatherContextGateway returning(WeatherContext value) {
        this.context = value;
        return this;
    }

    public FakeWeatherContextGateway thatFails() {
        this.throwOnCall = true;
        return this;
    }

    @Override
    public WeatherContext contextFor(Trip trip) {
        if (throwOnCall) {
            throw new IllegalStateException("weather service unavailable");
        }
        return context;
    }
}
