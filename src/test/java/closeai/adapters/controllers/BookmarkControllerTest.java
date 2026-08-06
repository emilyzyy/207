package closeai.adapters.controllers;

import closeai.adapters.presenters.ActivityDiscoveryPresenter;
import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.application.usecases.BookmarkActivityUseCase;
import closeai.application.usecases.RemoveBookmarkUseCase;
import closeai.domain.entities.Activity;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import closeai.infrastructure.persistence.CachedPlacesRepository;
import closeai.infrastructure.persistence.InMemoryItineraryDataAccessObject;
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
