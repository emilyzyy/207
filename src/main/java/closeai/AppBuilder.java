package closeai;

import closeai.application.AppContainer;
import closeai.application.ports.WeatherService;
import closeai.application.scheduling.DefaultActivityScoringPolicy;
import closeai.infrastructure.mock.MockDistanceService;
import closeai.infrastructure.mock.MockPlacesService;
import closeai.infrastructure.mock.MockWeatherService;
import closeai.infrastructure.persistence.InMemoryItineraryDataAccessObject;
import closeai.infrastructure.weather.OpenMeteoWeatherService;

/** Outer composition root for selecting infrastructure without leaking it into application code. */
public final class AppBuilder {
    public AppContainer build() {
        String weatherMode = System.getProperty("closeai.weather.mode", "mock");
        return "open-meteo".equalsIgnoreCase(weatherMode) ? buildLive() : buildOffline();
    }

    public AppContainer buildOffline() {
        return buildWithWeather(new MockWeatherService());
    }

    public AppContainer buildLive() {
        return buildWithWeather(new OpenMeteoWeatherService());
    }

    private AppContainer buildWithWeather(WeatherService weather) {
        InMemoryItineraryDataAccessObject itineraries = new InMemoryItineraryDataAccessObject();
        MockPlacesService places = new MockPlacesService();
        return new AppContainer(itineraries, places, places, new MockDistanceService(), weather,
                new DefaultActivityScoringPolicy(), itineraries);
    }
}
