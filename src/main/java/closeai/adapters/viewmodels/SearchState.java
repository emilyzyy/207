package closeai.adapters.viewmodels;

import closeai.domain.entities.Activity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable seeded activity-discovery display state. */
public final class SearchState {
    private final List<Activity> activities;
    private final String query;

    public SearchState(List<Activity> activities, String query) {
        this.activities = Collections.unmodifiableList(new ArrayList<Activity>(
                activities == null ? Collections.emptyList() : activities));
        this.query = query == null ? "" : query;
    }

    public List<Activity> getActivities() {
        return activities;
    }

    public String getQuery() {
        return query;
    }
}
