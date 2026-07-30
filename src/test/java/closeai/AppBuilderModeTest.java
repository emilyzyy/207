package closeai;

import closeai.application.AppContainer;
import closeai.infrastructure.mock.MockPlacesService;
import closeai.infrastructure.mock.MockWeatherService;
import closeai.infrastructure.persistence.CachedPlacesRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppBuilderModeTest {

    @AfterEach
    void clearModes() {
        System.clearProperty("closeai.weather.mode");
        System.clearProperty("closeai.places.mode");
        System.clearProperty("closeai.map.tiles.mode");
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
