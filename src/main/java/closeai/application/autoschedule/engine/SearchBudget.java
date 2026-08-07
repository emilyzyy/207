package closeai.application.autoschedule.engine;

/**
 * A node budget bounding the search.
 *
 * <p>The limit is counted in explored nodes rather than elapsed wall-clock time so
 * that the same input always explores the same tree and produces the same schedule.
 * A time-based limit would make results depend on machine load.</p>
 */
public final class SearchBudget {
    /** Default ceiling: ample for the 3-8 activity day plans this feature targets. */
    public static final int DEFAULT_MAX_NODES = 200_000;

    private final int maxNodes;
    private int usedNodes;

    public SearchBudget(int maxNodes) {
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("Node budget must be positive");
        }
        this.maxNodes = maxNodes;
    }

    public static SearchBudget defaultBudget() {
        return new SearchBudget(DEFAULT_MAX_NODES);
    }

    /** Consumes one node; false once the budget is spent. */
    public boolean consume() {
        if (usedNodes >= maxNodes) {
            return false;
        }
        usedNodes++;
        return true;
    }

    public boolean isExhausted() {
        return usedNodes >= maxNodes;
    }

    public int getUsedNodes() {
        return usedNodes;
    }

    public int getMaxNodes() {
        return maxNodes;
    }
}
