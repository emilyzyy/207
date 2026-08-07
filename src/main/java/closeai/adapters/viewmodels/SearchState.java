package closeai.adapters.viewmodels;

import closeai.domain.entities.Activity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable seeded activity-discovery display state. */
public final class SearchState {
    private final List<Activity> activities;
    private final String query;
    private final Set<String> bookmarkedIds;
    private final Set<String> scheduledIds;
    private final String selectedActivityId;
    private final boolean loading;

    public SearchState(List<Activity> activities, String query) {
        this(activities, query, Collections.emptySet(), Collections.emptySet(), null, false);
    }

    public SearchState(List<Activity> activities, String query,
                       Set<String> bookmarkedIds, Set<String> scheduledIds) {
        this(activities, query, bookmarkedIds, scheduledIds, null, false);
    }

    public SearchState(List<Activity> activities, String query,
                       Set<String> bookmarkedIds, Set<String> scheduledIds,
                       String selectedActivityId) {
        this(activities, query, bookmarkedIds, scheduledIds, selectedActivityId, false);
    }

    public SearchState(List<Activity> activities, String query,
                       Set<String> bookmarkedIds, Set<String> scheduledIds,
                       String selectedActivityId, boolean loading) {
        this.activities = Collections.unmodifiableList(new ArrayList<Activity>(
                activities == null ? Collections.emptyList() : activities));
        this.query = query == null ? "" : query;
        this.bookmarkedIds = Collections.unmodifiableSet(new HashSet<String>(
                bookmarkedIds == null ? Collections.emptySet() : bookmarkedIds));
        this.scheduledIds = Collections.unmodifiableSet(new HashSet<String>(
                scheduledIds == null ? Collections.emptySet() : scheduledIds));
        this.selectedActivityId = selectedActivityId;
        this.loading = loading;
    }

    public List<Activity> getActivities() {
        return activities;
    }

    public String getQuery() {
        return query;
    }

    public Set<String> getBookmarkedIds() {
        return bookmarkedIds;
    }

    public Set<String> getScheduledIds() {
        return scheduledIds;
    }

    public String getSelectedActivityId() {
        return selectedActivityId;
    }

    public boolean isLoading() {
        return loading;
    }
}
