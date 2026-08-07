package closeai.adapters.views;

import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.domain.entities.Activity;
import closeai.infrastructure.mock.MockPlacesService;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OverviewPanelMapTest {

    @Test
    void mapObservesSearchViewModelWithoutEnablingNetworkTiles() {
        System.setProperty("closeai.map.tiles.mode", "offline");
        DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("", null, "", ""));
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        OverviewPanel overview = new OverviewPanel(dashboard, search);
        Activity activity = new MockPlacesService().findAll().get(0);

        search.setState(new SearchState(
                Collections.singletonList(activity), ""));

        assertEquals(1, overview.getMapPanel().getActivityCount());
        assertFalse(overview.getMapPanel().isTileLoadingEnabled());
    }

    @Test
    void viewportLoaderMergesPlacesForTheVisibleBounds() throws Exception {
        System.setProperty("closeai.map.tiles.mode", "offline");
        DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("Toronto", null, "", ""));
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        OverviewPanel overview = new OverviewPanel(dashboard, search);
        MapPanel map = overview.getMapPanel();
        map.setSize(620, 520);

        CountDownLatch loaded = new CountDownLatch(1);
        List<Activity> mock = new MockPlacesService().findAll();
        map.setViewportLoader((south, west, north, east, max) -> {
            loaded.countDown();
            return mock;
        });
        map.flyTo(43.65, -79.38);
        map.reloadViewport();

        assertTrue(loaded.await(5, TimeUnit.SECONDS));

        for (int i = 0; i < 50 && map.getActivityCount() == 0; i++) {
            Thread.sleep(50);
        }
        assertEquals(mock.size(), map.getActivityCount());
    }
}
