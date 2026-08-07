package closeai.adapters.views;

import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.valueobjects.EventType;
import closeai.infrastructure.mock.MockPlacesService;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

    @Test
    void markerLabelsFollowBookmarkAndOrderedDayPlanState() {
        MapPanel map = new MapPanel(600, 500, false);
        Activity plain = new MockPlacesService().findAll().get(0);
        Activity bookmark = new MockPlacesService().findAll().get(1);
        Activity first = new MockPlacesService().findAll().get(2);
        Activity secondBookmarked = new MockPlacesService().findAll().get(3);
        map.setActivities(Arrays.asList(plain, bookmark, first, secondBookmarked));
        map.setHighlightedIds(
                new HashSet<>(Arrays.asList(bookmark.getId(), secondBookmarked.getId())),
                new HashSet<>(Arrays.asList(first.getId(), secondBookmarked.getId())));
        map.setSchedule(Arrays.asList(
                event("first", first, LocalTime.of(10, 0)),
                event("second", secondBookmarked, LocalTime.of(12, 0))));

        assertEquals("pin", map.markerText(plain.getId()));
        assertEquals("bookmark", map.markerText(bookmark.getId()));
        assertEquals("1", map.markerText(first.getId()));
        assertEquals("2", map.markerText(secondBookmarked.getId()));

        map.selectActivity(secondBookmarked);
        assertEquals(secondBookmarked.getId(), map.getSelectedActivityId());
    }

    private ScheduledEvent event(String id, Activity activity, LocalTime start) {
        return new ScheduledEvent(id, activity, start,
                start.plusMinutes(activity.getEstimatedDurationMinutes()),
                EventType.ACTIVITY, "Visit");
    }
}
