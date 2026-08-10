package interface_adapter.viewmodels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;

final class ViewModelStateTest {

    @Test
    void activityStatesUseDefensiveSnapshotsAndViewModelsPublishChanges() {
        final List<Activity> source = new ArrayList<Activity>();
        source.add(activity("rom"));
        final SearchViewModel search = new SearchViewModel(new SearchState(source, ""));
        final BookmarksViewModel bookmarks = new BookmarksViewModel(new BookmarksState(source));
        final AtomicInteger searchChanges = new AtomicInteger();
        final AtomicInteger bookmarkChanges = new AtomicInteger();
        search.addPropertyChangeListener(event -> searchChanges.incrementAndGet());
        bookmarks.addPropertyChangeListener(event -> bookmarkChanges.incrementAndGet());

        source.add(activity("cn-tower"));

        assertEquals(1, search.getState().getActivities().size());
        assertEquals(1, bookmarks.getState().getBookmarks().size());
        assertThrows(UnsupportedOperationException.class,
                () -> search.getState().getActivities().clear());

        search.setState(new SearchState(source, "tower"));
        bookmarks.setState(new BookmarksState(source));

        assertEquals(1, searchChanges.get());
        assertEquals(1, bookmarkChanges.get());
        assertEquals("tower", search.getState().getQuery());
        assertEquals(2, bookmarks.getState().getBookmarks().size());
    }

    @Test
    void dashboardAndTripOptionsExposeSeededTripInformation() {
        final DashboardState dashboardState = new DashboardState(
                "Toronto", LocalDate.of(2026, 7, 23),
                "Sunny intervals", "24°C · Good conditions");
        final DashboardViewModel dashboard = new DashboardViewModel(dashboardState);
        final TripOptionsState optionsState = new TripOptionsState(
                "Toronto", LocalDate.of(2026, 7, 23),
                LocalTime.of(9, 0), LocalTime.of(18, 0));
        final TripOptionsViewModel options = new TripOptionsViewModel(optionsState);

        assertEquals("Toronto", dashboard.getState().getDestination());
        assertEquals("Sunny intervals", dashboard.getState().getWeatherCondition());
        assertEquals(LocalTime.of(9, 0), options.getState().getStartTime());
        assertEquals(LocalTime.of(18, 0), options.getState().getEndTime());
    }

    private Activity activity(String id) {
        return new Activity(id, id, ActivityCategory.ATTRACTION,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(9, 0), LocalTime.of(20, 0),
                IndoorOutdoorType.INDOOR, "Low");
    }
}
