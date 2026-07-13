package closeai;

import closeai.application.AppContainer;
import closeai.application.ports.WeatherService;
import closeai.infrastructure.mock.MockDistanceService;
import closeai.infrastructure.mock.MockPlacesService;
import closeai.infrastructure.mock.MockWeatherService;
import closeai.infrastructure.persistence.InMemoryTripRepository;

/** Outer composition root for selecting infrastructure without leaking it into application code. */
public final class AppBuilder {
    public AppContainer build() {
        return buildOffline();
    }

    public AppContainer buildOffline() {
        return buildWithWeather(new MockWeatherService());
    }

    private AppContainer buildWithWeather(WeatherService weather) {
        InMemoryTripRepository trips = new InMemoryTripRepository();
        MockPlacesService places = new MockPlacesService();
        return new AppContainer(trips, places, places, new MockDistanceService(), weather);
    }
}
