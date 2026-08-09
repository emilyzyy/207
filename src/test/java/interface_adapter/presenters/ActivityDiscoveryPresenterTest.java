package interface_adapter.presenters;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import interface_adapter.viewmodels.BookmarksState;
import interface_adapter.viewmodels.BookmarksViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import java.time.LocalTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import use_case.search.SearchFailure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ActivityDiscoveryPresenterTest {

    @Test
    void transientFailureKeepsExistingActivitiesVisible() {
        Activity existing = activity();
        SearchViewModel search = new SearchViewModel(new SearchState(
                Collections.singletonList(existing), ""));
        ActivityDiscoveryPresenter presenter = new ActivityDiscoveryPresenter(
                search, new BookmarksViewModel(new BookmarksState(Collections.emptyList())));

        presenter.presentSearchResult(Collections.emptyList(), "park", null, 0.0,
                null, SearchFailure.SERVICE_UNAVAILABLE, false, "Toronto");

        assertEquals(Collections.singletonList(existing), search.getState().getActivities());
        assertTrue(search.getState().getFeedback().contains("temporarily unavailable"));
    }

    @Test
    void genuineNoMatchClearsOldResultsAndShowsContext() {
        SearchViewModel search = new SearchViewModel(new SearchState(
                Collections.singletonList(activity()), ""));
        ActivityDiscoveryPresenter presenter = new ActivityDiscoveryPresenter(
                search, new BookmarksViewModel(new BookmarksState(Collections.emptyList())));

        presenter.presentSearchResult(Collections.emptyList(), "aquarium", null, 0.0,
                null, SearchFailure.NO_MATCH, false, "Toronto");

        assertTrue(search.getState().getActivities().isEmpty());
        assertTrue(search.getState().getFeedback().contains("aquarium"));
        assertTrue(search.getState().getFeedback().contains("Toronto"));
    }

    private static Activity activity() {
        return new Activity("museum", "Museum", ActivityCategory.MUSEUM,
                new Location(43.6, -79.4, "Toronto"), 0.0, 60,
                LocalTime.of(9, 0), LocalTime.of(17, 0),
                IndoorOutdoorType.INDOOR, "Low");
    }
}
