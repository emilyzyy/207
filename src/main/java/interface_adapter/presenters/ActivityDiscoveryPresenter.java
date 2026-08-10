package interface_adapter.presenters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.SwingUtilities;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import interface_adapter.viewmodels.BookmarksState;
import interface_adapter.viewmodels.BookmarksViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import use_case.search.SearchFailure;

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

    /**
     * Performs the p re se nt re su lt s operation.
     * @param query the q ue ry value
     * @param activities the a ct iv it ie s value
     */
    public void presentResults(List<Activity> activities, String query,
                               ActivityCategory category, double minimumRating,
                               IndoorOutdoorType type) {
        presentResults(activities, query, category, minimumRating, type, "");
    }

    /**
     * Performs the p re se nt re su lt s operation.
     * @param query the q ue ry value
     * @param activities the a ct iv it ie s value
     */
    public void presentResults(List<Activity> activities, String query,
                               ActivityCategory category, double minimumRating,
                               IndoorOutdoorType type, String feedback) {
        runOnEventThread(() -> {
            final SearchState current = search.getState();
            search.setState(new SearchState(
                    activities, query, current.getBookmarkedIds(), current.getScheduledIds(),
                    category, minimumRating, type, feedback));
        });
    }

    /**
     * Presents a complete search outcome while retaining useful cards during transient outages.
     * @param query the q ue ry value
     * @param activities the a ct iv it ie s value
     */
    public void presentSearchResult(List<Activity> activities, String query,
                                    ActivityCategory category, double minimumRating,
                                    IndoorOutdoorType type, SearchFailure failure,
                                    boolean partial, String destination) {
        final String feedback = ActivitySearchFeedback.format(
                failure, partial, query, destination);
        runOnEventThread(() -> {
            final SearchState current = search.getState();
            final boolean transientFailure = failure == SearchFailure.RATE_LIMITED
                    || failure == SearchFailure.SERVICE_UNAVAILABLE;
            final List<Activity> displayed = transientFailure
                    && (activities == null || activities.isEmpty())
                    ? current.getActivities() : activities;
            search.setState(new SearchState(
                    displayed, query, current.getBookmarkedIds(), current.getScheduledIds(),
                    category, minimumRating, type, feedback));
        });
    }

    /**
     * Performs the p re se nt tr ip operation.
     * @param trip the t ri p value
     */
    public void presentTrip(Trip trip) {
        final Set<String> bookmarkedIds = new HashSet<>();
        for (Activity activity : trip.getBookmarkedActivities()) {
            bookmarkedIds.add(activity.getId());
        }
        final Set<String> scheduledIds = new HashSet<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getActivity() != null) {
                scheduledIds.add(event.getActivity().getId());
            }
        }
        runOnEventThread(() -> {
            final SearchState current = search.getState();
            search.setState(new SearchState(
                    current.getActivities(), current.getQuery(), bookmarkedIds, scheduledIds,
                    current.getCategory(), current.getMinimumRating(), current.getType(), ""));
            bookmarks.setState(new BookmarksState(trip.getBookmarkedActivities()));
        });
    }

    /**
     * Performs the p re se nt fa il ur e operation.
     * @param message the m es sa ge value
     */
    public void presentFailure(String message) {
        runOnEventThread(() -> {
            final SearchState current = search.getState();
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
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not update activity discovery view", exception);
        }
    }
}
