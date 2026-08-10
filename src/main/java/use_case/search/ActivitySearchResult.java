package use_case.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import entity.entities.Activity;

/** Search output that distinguishes no matches from unavailable external services. */
public final class ActivitySearchResult {
    private final List<Activity> activities;
    private final SearchSource source;
    private final boolean partial;
    private final SearchFailure failure;

    public ActivitySearchResult(List<Activity> activities, SearchSource source,
                                boolean partial, SearchFailure failure) {
        this.activities = Collections.unmodifiableList(new ArrayList<>(
                activities == null ? Collections.emptyList() : activities));
        this.source = source == null ? SearchSource.LOCAL : source;
        this.partial = partial;
        this.failure = failure == null ? SearchFailure.NONE : failure;
    }

    public List<Activity> getActivities() {
        return activities;
    }

    public SearchSource getSource() {
        return source;
    }

    public boolean isPartial() {
        return partial;
    }

    public SearchFailure getFailure() {
        return failure;
    }
}
