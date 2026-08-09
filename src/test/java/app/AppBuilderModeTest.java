package app;

import app.AppContainer;
import interface_adapter.mock.MockPlacesService;
import interface_adapter.mock.MockWeatherService;
import database.persistence.CachedPlacesRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppBuilderModeTest {

    @AfterEach
    void clearModes() {
        System.clearProperty("trippy.weather.mode");
        System.clearProperty("trippy.places.mode");
        System.clearProperty("trippy.map.tiles.mode");
    }

    @Test
    void defaultBuildUsesOfflineServicesAndASeededActivityCache() {
        AppContainer app = new AppBuilder().build();

        assertTrue(app.weather instanceof MockWeatherService);
        assertTrue(app.places instanceof MockPlacesService);
        assertTrue(app.activities instanceof CachedPlacesRepository);
        assertEquals(7, app.activities.findAll().size());
    }
}
