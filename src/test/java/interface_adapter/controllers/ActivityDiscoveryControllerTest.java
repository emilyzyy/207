package interface_adapter.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import interface_adapter.presenters.ActivityDiscoveryPresenter;
import interface_adapter.viewmodels.BookmarksState;
import interface_adapter.viewmodels.BookmarksViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import use_case.search.ActivitySearchResult;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;
import use_case.usecases.FilterActivitiesUseCase;
import use_case.usecases.SearchActivitiesUseCase;

final class ActivityDiscoveryControllerTest {
    @Test
    void searchesCurrentDestinationAndAppliesAllFilters() {
        Activity museum = activity("museum", ActivityCategory.MUSEUM, 4.8,
                IndoorOutdoorType.INDOOR);
        Activity food = activity("food", ActivityCategory.FOOD, 4.2,
                IndoorOutdoorType.INDOOR);
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        ActivityDiscoveryController controller = new ActivityDiscoveryController(
                new SearchActivitiesUseCase(request -> {
                    assertEquals("Montreal", request.getDestination());
                    assertEquals("m", request.getQuery());
                    return new ActivitySearchResult(Arrays.asList(museum, food),
                            SearchSource.LOCAL, false, SearchFailure.NONE);
                }),
                new FilterActivitiesUseCase(),
                () -> "Montreal",
                new ActivityDiscoveryPresenter(search, new BookmarksViewModel(
                        new BookmarksState(Collections.emptyList()))));

        controller.execute(" m ", ActivityCategory.MUSEUM, 4.5,
                IndoorOutdoorType.INDOOR);

        assertEquals(Collections.singletonList(museum), search.getState().getActivities());
        assertEquals("m", search.getState().getQuery());
        assertEquals(ActivityCategory.MUSEUM, search.getState().getCategory());
        assertEquals(4.5, search.getState().getMinimumRating());
    }

    @Test
    void reportsThatTripIsRequiredWithoutCallingTheService() {
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        ActivityDiscoveryController controller = new ActivityDiscoveryController(
                new SearchActivitiesUseCase(request -> {
                    throw new AssertionError("service must not be called");
                }),
                new FilterActivitiesUseCase(), () -> "",
                new ActivityDiscoveryPresenter(search, new BookmarksViewModel(
                        new BookmarksState(Collections.emptyList()))));

        controller.execute("museum", null, 0, null);

        assertTrue(search.getState().getFeedback().contains("Create a trip"));
    }

    @Test
    void presentsCachedMatchesWhenRemoteSearchIsRateLimited() {
        Activity cachedMuseum = activity("cached-museum", ActivityCategory.MUSEUM,
                0.0, IndoorOutdoorType.INDOOR);
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        ActivityDiscoveryController controller = new ActivityDiscoveryController(
                new SearchActivitiesUseCase(request -> new ActivitySearchResult(
                        Collections.singletonList(cachedMuseum),
                        SearchSource.LOCAL, true, SearchFailure.RATE_LIMITED)),
                new FilterActivitiesUseCase(), () -> "Toronto",
                new ActivityDiscoveryPresenter(search, new BookmarksViewModel(
                        new BookmarksState(Collections.emptyList()))));

        controller.execute("museum", ActivityCategory.MUSEUM, 0.0,
                IndoorOutdoorType.INDOOR);

        assertEquals(Collections.singletonList(cachedMuseum),
                search.getState().getActivities());
        assertEquals("museum", search.getState().getQuery());
        assertTrue(search.getState().getFeedback().contains("Showing saved results"));
        assertTrue(search.getState().getFeedback().contains("busy"));
    }

    private Activity activity(String id, ActivityCategory category, double rating,
                              IndoorOutdoorType type) {
        return new Activity(id, id, category, new Location(43, -79, id), rating, 60,
                LocalTime.of(9, 0), LocalTime.of(18, 0), type, "Low");
    }
}
