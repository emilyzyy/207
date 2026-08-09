package interface_adapter.controllers;

import interface_adapter.presenters.ActivityDiscoveryPresenter;
import interface_adapter.viewmodels.BookmarksState;
import interface_adapter.viewmodels.BookmarksViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import use_case.usecases.BookmarkActivityUseCase;
import use_case.usecases.RemoveBookmarkUseCase;
import entity.entities.Activity;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import database.persistence.CachedPlacesRepository;
import database.persistence.InMemoryItineraryDataAccessObject;
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
