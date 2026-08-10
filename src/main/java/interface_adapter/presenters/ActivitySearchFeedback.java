package interface_adapter.presenters;

import use_case.search.SearchFailure;

/** Converts search outcomes into concise, actionable, non-technical UI messages. */
final class ActivitySearchFeedback {
    private ActivitySearchFeedback() {

    }

    static String format(SearchFailure failure, boolean partial,
                         String query, String destination) {
        final SearchFailure outcome = failure == null ? SearchFailure.NONE : failure;
        final String place = clean(destination, "this destination");
        final String terms = clean(query, "");
        if (partial && outcome == SearchFailure.RATE_LIMITED) {
            return "Showing saved results while OpenStreetMap is busy right now.";
        }
        if (partial && outcome == SearchFailure.SERVICE_UNAVAILABLE) {
            return "Showing saved results while OpenStreetMap is temporarily unavailable.";
        }
        switch (outcome) {
            case NONE:
                return "";
            case NO_MATCH:
                return terms.isEmpty()
                        ? "No supported activities were found near " + place
                        + ". Try zooming out or searching by name."
                        : "No activities matched \"" + terms + "\" near " + place
                        + ". Try a broader name or different filters.";
            case INVALID_DESTINATION:
                return "We couldn't locate the trip destination. Check it and try again.";
            case RATE_LIMITED:
                return "OpenStreetMap is busy right now. Please wait a moment and try again.";
            default:
                return "Activities couldn't be loaded because OpenStreetMap is temporarily "
                        + "unavailable.";
        }
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
