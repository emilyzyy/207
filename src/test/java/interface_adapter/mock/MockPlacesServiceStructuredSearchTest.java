package interface_adapter.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;
import use_case.usecases.SearchActivitiesInteractor;

final class MockPlacesServiceStructuredSearchTest {

    @Test
    void offlineSearchReturnsACompleteStructuredLocalResult() {
        final SearchActivitiesInteractor interactor = new SearchActivitiesInteractor(
                new MockPlacesService());
        final ActivitySearchRequest request = new ActivitySearchRequest(
                "Toronto", "Royal Ontario Museum", null, null, 25);

        final ActivitySearchResult result = interactor.execute(request);

        assertEquals(1, result.getActivities().size());
        assertEquals("rom", result.getActivities().get(0).getId());
        assertEquals(SearchSource.LOCAL, result.getSource());
        assertEquals(SearchFailure.NONE, result.getFailure());
        assertFalse(result.isPartial());
    }

    @Test
    void offlineNoMatchIsNotMisreportedAsAServiceFailure() {
        final SearchActivitiesInteractor interactor = new SearchActivitiesInteractor(
                new MockPlacesService());
        final ActivitySearchRequest request = new ActivitySearchRequest(
                "Toronto", "Place That Does Not Exist", null, null, 25);

        final ActivitySearchResult result = interactor.execute(request);

        assertTrue(result.getActivities().isEmpty());
        assertEquals(SearchSource.LOCAL, result.getSource());
        assertEquals(SearchFailure.NO_MATCH, result.getFailure());
        assertFalse(result.isPartial());
    }
}
