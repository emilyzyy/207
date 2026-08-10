package interface_adapter.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import use_case.search.SearchFailure;

final class ActivitySearchFeedbackTest {

    @Test
    void noMatchExplainsTheQueryAndDestination() {
        final String message = ActivitySearchFeedback.format(
                SearchFailure.NO_MATCH, false, "museum", "Toronto");

        assertTrue(message.contains("\"museum\""));
        assertTrue(message.contains("Toronto"));
        assertTrue(message.contains("broader name"));
    }

    @Test
    void emptyDiscoverySuggestsZoomingOrNamedSearch() {
        final String message = ActivitySearchFeedback.format(
                SearchFailure.NO_MATCH, false, "", "Milan");

        assertTrue(message.contains("Milan"));
        assertTrue(message.contains("zooming out"));
    }

    @Test
    void eachExternalFailureHasActionableFeedback() {
        assertTrue(ActivitySearchFeedback.format(SearchFailure.INVALID_DESTINATION,
                false, "", "").contains("Check it"));
        assertTrue(ActivitySearchFeedback.format(SearchFailure.RATE_LIMITED,
                false, "", "Toronto").contains("wait a moment"));
        assertTrue(ActivitySearchFeedback.format(SearchFailure.SERVICE_UNAVAILABLE,
                false, "", "Toronto").contains("temporarily unavailable"));
    }

    @Test
    void partialResultsExplainThatSavedResultsRemainVisible() {
        assertEquals("Showing saved results while OpenStreetMap is temporarily unavailable.",
                ActivitySearchFeedback.format(SearchFailure.SERVICE_UNAVAILABLE,
                        true, "park", "Toronto"));
        assertEquals("Showing saved results while OpenStreetMap is busy right now.",
                ActivitySearchFeedback.format(SearchFailure.RATE_LIMITED,
                        true, "park", "Toronto"));
    }
}
