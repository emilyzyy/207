package use_case.search;

import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;

/** Immutable input boundary for activity discovery. */
public final class ActivitySearchRequest {
    private final String destination;
    private final String query;
    private final ActivityCategory category;
    private final IndoorOutdoorType setting;
    private final int limit;

    public ActivitySearchRequest(String destination, String query,
                                 ActivityCategory category,
                                 IndoorOutdoorType setting, int limit) {
        this.destination = destination == null ? "" : destination.trim();
        this.query = query == null ? "" : query.trim();
        this.category = category;
        this.setting = setting;
        this.limit = Math.max(1, limit);
    }

    public String getDestination() { return destination; }
    public String getQuery() { return query; }
    public ActivityCategory getCategory() { return category; }
    public IndoorOutdoorType getSetting() { return setting; }
    public int getLimit() { return limit; }
}
