package trippy.adapters.viewmodels;

import trippy.domain.entities.Activity;
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.IndoorOutdoorType;
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
    private final ActivityCategory category;
    private final double minimumRating;
    private final IndoorOutdoorType type;
    private final String feedback;

    public SearchState(List<Activity> activities, String query) {
        this(activities, query, Collections.emptySet(), Collections.emptySet(), null, false,
                null, 0.0, null, "");
    }

    public SearchState(List<Activity> activities, String query,
                       Set<String> bookmarkedIds, Set<String> scheduledIds) {
        this(activities, query, bookmarkedIds, scheduledIds, null, false,
                null, 0.0, null, "");
    }

    public SearchState(List<Activity> activities, String query,
                       Set<String> bookmarkedIds, Set<String> scheduledIds,
                       String selectedActivityId) {
        this(activities, query, bookmarkedIds, scheduledIds, selectedActivityId, false,
                null, 0.0, null, "");
    }

    public SearchState(List<Activity> activities, String query,
                       Set<String> bookmarkedIds, Set<String> scheduledIds,
                       String selectedActivityId, boolean loading) {
        this(activities, query, bookmarkedIds, scheduledIds, selectedActivityId, loading,
                null, 0.0, null, "");
    }

    public SearchState(List<Activity> activities, String query,
                       Set<String> bookmarkedIds, Set<String> scheduledIds,
                       ActivityCategory category, double minimumRating,
                       IndoorOutdoorType type, String feedback) {
        this(activities, query, bookmarkedIds, scheduledIds, null, false,
                category, minimumRating, type, feedback);
    }

    public SearchState(List<Activity> activities, String query,
                       Set<String> bookmarkedIds, Set<String> scheduledIds,
                       String selectedActivityId, boolean loading,
                       ActivityCategory category, double minimumRating,
                       IndoorOutdoorType type, String feedback) {
        this.activities = Collections.unmodifiableList(new ArrayList<Activity>(
                activities == null ? Collections.emptyList() : activities));
        this.query = query == null ? "" : query;
        this.bookmarkedIds = Collections.unmodifiableSet(new HashSet<String>(
                bookmarkedIds == null ? Collections.emptySet() : bookmarkedIds));
        this.scheduledIds = Collections.unmodifiableSet(new HashSet<String>(
                scheduledIds == null ? Collections.emptySet() : scheduledIds));
        this.selectedActivityId = selectedActivityId;
        this.loading = loading;
        this.category = category;
        this.minimumRating = minimumRating;
        this.type = type;
        this.feedback = feedback == null ? "" : feedback;
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

    public ActivityCategory getCategory() { return category; }

    public double getMinimumRating() { return minimumRating; }

    public IndoorOutdoorType getType() { return type; }

    public String getFeedback() { return feedback; }
}
