package closeai.adapters.controllers;

import closeai.adapters.presenters.ActivityDiscoveryPresenter;
import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.application.usecases.FilterActivitiesUseCase;
import closeai.application.usecases.SearchActivitiesUseCase;
import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                new SearchActivitiesUseCase((destination, query) -> {
                    assertEquals("Montreal", destination);
                    assertEquals("m", query);
                    return Arrays.asList(museum, food);
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
                new SearchActivitiesUseCase((destination, query) -> {
                    throw new AssertionError("service must not be called");
                }),
                new FilterActivitiesUseCase(), () -> "",
                new ActivityDiscoveryPresenter(search, new BookmarksViewModel(
                        new BookmarksState(Collections.emptyList()))));

        controller.execute("museum", null, 0, null);

        assertTrue(search.getState().getFeedback().contains("Create a trip"));
    }

    private Activity activity(String id, ActivityCategory category, double rating,
                              IndoorOutdoorType type) {
        return new Activity(id, id, category, new Location(43, -79, id), rating, 60,
                LocalTime.of(9, 0), LocalTime.of(18, 0), type, "Low");
    }
}
