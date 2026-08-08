package trippy.adapters.controllers;

import trippy.adapters.presenters.ActivityDiscoveryPresenter;
import trippy.adapters.viewmodels.BookmarksState;
import trippy.adapters.viewmodels.BookmarksViewModel;
import trippy.adapters.viewmodels.SearchState;
import trippy.adapters.viewmodels.SearchViewModel;
import trippy.application.usecases.BookmarkActivityUseCase;
import trippy.application.usecases.RemoveBookmarkUseCase;
import trippy.domain.entities.Activity;
import trippy.domain.entities.Trip;
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.IndoorOutdoorType;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import trippy.infrastructure.persistence.CachedPlacesRepository;
import trippy.infrastructure.persistence.InMemoryItineraryDataAccessObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BookmarkControllerTest {
    @Test
    void togglingBookmarkSynchronizesSearchAndSavedViews() {
        Activity activity = new Activity("museum", "Museum", ActivityCategory.MUSEUM,
                new Location(43, -79, "Museum Road"), 4.8, 60,
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                IndoorOutdoorType.INDOOR, "Low");
        CachedPlacesRepository activities = new CachedPlacesRepository();
        activities.addAll(Collections.singletonList(activity));
        InMemoryItineraryDataAccessObject trips = new InMemoryItineraryDataAccessObject();
        Trip trip = new Trip("trip-1", "Toronto", LocalDate.of(2026, 8, 7),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
        trips.save(trip);
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.singletonList(activity), ""));
        BookmarksViewModel bookmarks = new BookmarksViewModel(
                new BookmarksState(Collections.emptyList()));
        ActivityDiscoveryPresenter presenter = new ActivityDiscoveryPresenter(search, bookmarks);
        BookmarkController controller = new BookmarkController(
                new BookmarkActivityUseCase(trips, activities), new RemoveBookmarkUseCase(trips),
                () -> trip.getId(), search, presenter);

        controller.toggle(activity.getId());
        assertTrue(search.getState().getBookmarkedIds().contains(activity.getId()));
        assertEquals(Collections.singletonList(activity), bookmarks.getState().getBookmarks());

        controller.toggle(activity.getId());
        assertTrue(search.getState().getBookmarkedIds().isEmpty());
        assertTrue(bookmarks.getState().getBookmarks().isEmpty());
    }
}
