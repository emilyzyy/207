package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import interface_adapter.mock.MockPlacesService;
import interface_adapter.viewmodels.ActivitySelectionViewModel;
import interface_adapter.viewmodels.DashboardState;
import interface_adapter.viewmodels.DashboardViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;

final class OverviewPanelMapTest {

    @Test
    void mapObservesSearchViewModelWithoutEnablingNetworkTiles() {
        System.setProperty("trippy.map.tiles.mode", "offline");
        final DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("", null, "", ""));
        final SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        final OverviewPanel overview = new OverviewPanel(dashboard, search);
        final Activity activity = new MockPlacesService().findAll().get(0);

        search.setState(new SearchState(
                Collections.singletonList(activity), ""));

        assertEquals(1, overview.getMapPanel().getActivityCount());
        assertFalse(overview.getMapPanel().isTileLoadingEnabled());
    }

    @Test
    void viewportLoaderMergesPlacesForTheVisibleBounds() throws Exception {
        System.setProperty("trippy.map.tiles.mode", "offline");
        final DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("Toronto", null, "", ""));
        final SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        final OverviewPanel overview = new OverviewPanel(dashboard, search);
        final MapPanel map = overview.getMapPanel();
        map.setSize(620, 520);

        final CountDownLatch loaded = new CountDownLatch(1);
        final AtomicInteger requestedLimit = new AtomicInteger();
        final List<Activity> mock = new MockPlacesService().findAll();
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
        final String destination = "Toronto";
        final DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState(destination, null, "", ""));
        final SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        final OverviewPanel overview = new OverviewPanel(dashboard, search);
        final MapPanel map = overview.getMapPanel();
        map.setSize(620, 520);

        final double[] home = StaticTileLoader.latLngForCity(destination);
        assertNotNull(home);

        // setViewportLoader schedules an immediate reload on the EDT while the map is still
        // on the trip city. Ignore those loads so this test only asserts the far-from-home skip.
        final AtomicBoolean armed = new AtomicBoolean(false);
        final CountDownLatch loaded = new CountDownLatch(1);
        map.setViewportLoader((south, west, north, east, max) -> {
            if (armed.get()) {
                loaded.countDown();
            }
            return new MockPlacesService().findAll();
        });
        map.flyTo(home[0] + 5, home[1]);
        armed.set(true);
        map.reloadViewport();

        assertFalse(loaded.await(1, TimeUnit.SECONDS));
        assertEquals(0, map.getActivityCount());
    }

    @Test
    void markerLabelsFollowBookmarkAndOrderedDayPlanState() {
        final MapPanel map = new MapPanel(600, 500, false);
        final Activity plain = new MockPlacesService().findAll().get(0);
        final Activity bookmark = new MockPlacesService().findAll().get(1);
        final Activity first = new MockPlacesService().findAll().get(2);
        final Activity secondBookmarked = new MockPlacesService().findAll().get(3);
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
        final MapPanel map = new MapPanel(600, 500, false);
        final List<Activity> places = new MockPlacesService().findAll();
        final Activity selectedGeneric = places.get(0);
        final Activity planned = places.get(1);
        final Activity generic = places.get(2);
        final Activity bookmarked = places.get(3);
        map.setActivities(Arrays.asList(
                planned, selectedGeneric, bookmarked, generic));
        map.setHighlightedIds(
                Collections.singleton(bookmarked.getId()),
                Collections.singleton(planned.getId()));
        map.selectActivity(selectedGeneric);

        final List<Activity> ordered = map.markerDrawingOrder();

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
        final Activity activity = new MockPlacesService().findAll().get(0);
        final DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("Toronto", null, "", ""));
        final SearchViewModel search = new SearchViewModel(new SearchState(
                Collections.singletonList(activity), ""));
        final ActivitySelectionViewModel selection = new ActivitySelectionViewModel();
        final OverviewPanel overview = new OverviewPanel(
                dashboard, search, null, null, selection);
        final MapPanel map = overview.getMapPanel();
        map.setSize(600, 500);
        map.selectActivity(activity);
        map.paint(new BufferedImage(600, 500, BufferedImage.TYPE_INT_ARGB).getGraphics());

        final MouseEvent press = new MouseEvent(map, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 300, 250, 1, false);
        final MouseEvent release = new MouseEvent(map, MouseEvent.MOUSE_RELEASED,
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
