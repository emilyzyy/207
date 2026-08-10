package interface_adapter.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import interface_adapter.viewmodels.BookmarksState;
import interface_adapter.viewmodels.BookmarksViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import use_case.search.SearchFailure;

final class ActivityDiscoveryPresenterTest {

    @Test
    void transientFailureKeepsExistingActivitiesVisible() {
        final Activity existing = activity();
        final SearchViewModel search = new SearchViewModel(new SearchState(
                Collections.singletonList(existing), ""));
        final ActivityDiscoveryPresenter presenter = new ActivityDiscoveryPresenter(
                search, new BookmarksViewModel(new BookmarksState(Collections.emptyList())));

        presenter.presentSearchResult(Collections.emptyList(), "park", null, 0.0,
                null, SearchFailure.SERVICE_UNAVAILABLE, false, "Toronto");

        assertEquals(Collections.singletonList(existing), search.getState().getActivities());
        assertTrue(search.getState().getFeedback().contains("temporarily unavailable"));
    }

    @Test
    void genuineNoMatchClearsOldResultsAndShowsContext() {
        final SearchViewModel search = new SearchViewModel(new SearchState(
                Collections.singletonList(activity()), ""));
        final ActivityDiscoveryPresenter presenter = new ActivityDiscoveryPresenter(
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
