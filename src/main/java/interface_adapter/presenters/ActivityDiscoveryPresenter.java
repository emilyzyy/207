package interface_adapter.presenters;

import interface_adapter.viewmodels.BookmarksState;
import interface_adapter.viewmodels.BookmarksViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import use_case.search.SearchFailure;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;

/** Maps activity-discovery results and bookmark changes into shared Swing state. */
public final class ActivityDiscoveryPresenter {
    private final SearchViewModel search;
    private final BookmarksViewModel bookmarks;

    public ActivityDiscoveryPresenter(SearchViewModel search, BookmarksViewModel bookmarks) {
        if (search == null || bookmarks == null) {
            throw new IllegalArgumentException("Activity discovery ViewModels are required");
        }
        this.search = search;
        this.bookmarks = bookmarks;
    }

    public void presentResults(List<Activity> activities, String query,
                               ActivityCategory category, double minimumRating,
                               IndoorOutdoorType type) {
        presentResults(activities, query, category, minimumRating, type, "");
    }

    public void presentResults(List<Activity> activities, String query,
                               ActivityCategory category, double minimumRating,
                               IndoorOutdoorType type, String feedback) {
        runOnEventThread(() -> {
            SearchState current = search.getState();
            search.setState(new SearchState(
                    activities, query, current.getBookmarkedIds(), current.getScheduledIds(),
                    category, minimumRating, type, feedback));
        });
    }

    /** Presents a complete search outcome while retaining useful cards during transient outages. */
    public void presentSearchResult(List<Activity> activities, String query,
                                    ActivityCategory category, double minimumRating,
                                    IndoorOutdoorType type, SearchFailure failure,
                                    boolean partial, String destination) {
        String feedback = ActivitySearchFeedback.format(
                failure, partial, query, destination);
        runOnEventThread(() -> {
            SearchState current = search.getState();
            boolean transientFailure = failure == SearchFailure.RATE_LIMITED
                    || failure == SearchFailure.SERVICE_UNAVAILABLE;
            List<Activity> displayed = transientFailure
                    && (activities == null || activities.isEmpty())
                    ? current.getActivities() : activities;
            search.setState(new SearchState(
                    displayed, query, current.getBookmarkedIds(), current.getScheduledIds(),
                    category, minimumRating, type, feedback));
        });
    }

    public void presentTrip(Trip trip) {
        Set<String> bookmarkedIds = new HashSet<>();
        for (Activity activity : trip.getBookmarkedActivities()) {
            bookmarkedIds.add(activity.getId());
        }
        Set<String> scheduledIds = new HashSet<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getActivity() != null) {
                scheduledIds.add(event.getActivity().getId());
            }
        }
        runOnEventThread(() -> {
            SearchState current = search.getState();
            search.setState(new SearchState(
                    current.getActivities(), current.getQuery(), bookmarkedIds, scheduledIds,
                    current.getCategory(), current.getMinimumRating(), current.getType(), ""));
            bookmarks.setState(new BookmarksState(trip.getBookmarkedActivities()));
        });
    }

    public void presentFailure(String message) {
        runOnEventThread(() -> {
            SearchState current = search.getState();
            search.setState(new SearchState(
                    current.getActivities(), current.getQuery(), current.getBookmarkedIds(),
                    current.getScheduledIds(), current.getCategory(), current.getMinimumRating(),
                    current.getType(), message == null ? "Unable to update activities" : message));
        });
    }

    private static void runOnEventThread(Runnable update) {
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(update);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not update activity discovery view", exception);
        }
    }
}
