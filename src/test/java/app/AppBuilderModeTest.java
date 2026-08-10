package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import database.persistence.CachedPlacesRepository;
import interface_adapter.mock.MockPlacesService;
import interface_adapter.mock.MockWeatherService;

final class AppBuilderModeTest {

    @AfterEach
    void clearModes() {
        System.clearProperty("trippy.weather.mode");
        System.clearProperty("trippy.places.mode");
        System.clearProperty("trippy.map.tiles.mode");
    }

    @Test
    void defaultBuildUsesOfflineServicesAndASeededActivityCache() {
        final AppContainer app = new AppBuilder().build();

        assertTrue(app.weather instanceof MockWeatherService);
        assertTrue(app.places instanceof MockPlacesService);
        assertTrue(app.activities instanceof CachedPlacesRepository);
        assertEquals(7, app.activities.findAll().size());
    }
}
