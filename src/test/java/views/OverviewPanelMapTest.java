package views;

import interface_adapter.viewmodels.DashboardState;
import interface_adapter.viewmodels.DashboardViewModel;
import interface_adapter.viewmodels.ActivitySelectionViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import interface_adapter.mock.MockPlacesService;
import java.time.LocalTime;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OverviewPanelMapTest {

    @Test
    void mapObservesSearchViewModelWithoutEnablingNetworkTiles() {
        System.setProperty("trippy.map.tiles.mode", "offline");
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
        System.setProperty("trippy.map.tiles.mode", "offline");
        DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("Toronto", null, "", ""));
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        OverviewPanel overview = new OverviewPanel(dashboard, search);
        MapPanel map = overview.getMapPanel();
        map.setSize(620, 520);

        CountDownLatch loaded = new CountDownLatch(1);
        AtomicInteger requestedLimit = new AtomicInteger();
        List<Activity> mock = new MockPlacesService().findAll();
        map.setViewportLoader((south, west, north, east, max) -> {
            requestedLimit.set(max);
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
        assertTrue(requestedLimit.get() <= 75);
    }

    @Test
    void viewportLoaderSkipsReloadWhenMapIsFarFromTripCity() throws Exception {
        System.setProperty("trippy.map.tiles.mode", "offline");
        String destination = "Toronto";
        DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState(destination, null, "", ""));
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        OverviewPanel overview = new OverviewPanel(dashboard, search);
        MapPanel map = overview.getMapPanel();
        map.setSize(620, 520);

        double[] home = StaticTileLoader.latLngForCity(destination);
        assertNotNull(home);

        // Count only loads whose viewport is far from the trip city. Home-city reloads from
        // setViewportLoader / flyTo races must not fail the assertion (they are allowed).
        AtomicInteger farLoads = new AtomicInteger();
        map.setViewportLoader((south, west, north, east, max) -> {
            double midLat = (south + north) / 2.0;
            double midLng = (west + east) / 2.0;
            if (Math.hypot(midLat - home[0], midLng - home[1]) > 1.0) {
                farLoads.incrementAndGet();
            }
            return Collections.emptyList();
        });
        map.flyTo(home[0] + 5, home[1]);
        map.reloadViewport();
        SwingUtilities.invokeAndWait(() -> { });
        // In-flight executor work from a near-home reload may still finish; give it a moment.
        Thread.sleep(300);

        assertEquals(0, farLoads.get());
        assertEquals(0, map.getActivityCount());
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

    @Test
    void markersDrawGenericThenBookmarkedThenPlannedAndSelectedLast() {
        MapPanel map = new MapPanel(600, 500, false);
        List<Activity> places = new MockPlacesService().findAll();
        Activity selectedGeneric = places.get(0);
        Activity planned = places.get(1);
        Activity generic = places.get(2);
        Activity bookmarked = places.get(3);
        map.setActivities(Arrays.asList(
                planned, selectedGeneric, bookmarked, generic));
        map.setHighlightedIds(
                Collections.singleton(bookmarked.getId()),
                Collections.singleton(planned.getId()));
        map.selectActivity(selectedGeneric);

        List<Activity> ordered = map.markerDrawingOrder();

        assertEquals(generic.getId(), ordered.get(0).getId());
        assertEquals(bookmarked.getId(), ordered.get(1).getId());
        assertEquals(planned.getId(), ordered.get(2).getId());
        assertEquals(selectedGeneric.getId(), ordered.get(3).getId());
        assertTrue(map.markerRadius(generic.getId())
                < map.markerRadius(bookmarked.getId()));
        assertTrue(map.markerRadius(bookmarked.getId())
                < map.markerRadius(selectedGeneric.getId()));
    }

    @Test
    void clickingMarkerSelectsSearchCardAndKeepsMapFocused() {
        System.setProperty("trippy.map.tiles.mode", "offline");
        Activity activity = new MockPlacesService().findAll().get(0);
        DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("Toronto", null, "", ""));
        SearchViewModel search = new SearchViewModel(new SearchState(
                Collections.singletonList(activity), ""));
        ActivitySelectionViewModel selection = new ActivitySelectionViewModel();
        OverviewPanel overview = new OverviewPanel(
                dashboard, search, null, null, selection);
        MapPanel map = overview.getMapPanel();
        map.setSize(600, 500);
        map.selectActivity(activity);
        map.paint(new BufferedImage(600, 500, BufferedImage.TYPE_INT_ARGB).getGraphics());

        MouseEvent press = new MouseEvent(map, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 300, 250, 1, false);
        MouseEvent release = new MouseEvent(map, MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), 0, 300, 250, 1, false);
        for (java.awt.event.MouseListener listener : map.getMouseListeners()) {
            listener.mousePressed(press);
            listener.mouseReleased(release);
        }

        assertEquals(activity.getId(), selection.getSelectedActivityId());
        assertEquals(activity.getId(), search.getState().getSelectedActivityId());
        assertEquals(activity.getId(), map.getSelectedActivityId());
    }

    private ScheduledEvent event(String id, Activity activity, LocalTime start) {
        return new ScheduledEvent(id, activity, start,
                start.plusMinutes(activity.getEstimatedDurationMinutes()),
                EventType.ACTIVITY, "Visit");
    }
}
