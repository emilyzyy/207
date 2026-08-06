package closeai.application.autoschedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A complete proposed schedule: every activity placed, in time order, with its score. */
public final class SchedulePlan {
    private final List<PlacedActivity> placements;
    private final ScheduleScore score;

    public SchedulePlan(List<PlacedActivity> placements, ScheduleScore score) {
        if (placements == null || placements.isEmpty()) {
            throw new IllegalArgumentException("A schedule plan needs at least one placement");
        }
        if (score == null) {
            throw new IllegalArgumentException("Schedule score is required");
        }
        List<PlacedActivity> sorted = new ArrayList<>(placements);
        Collections.sort(sorted, (left, right) -> left.getStart().compareTo(right.getStart()));
        this.placements = Collections.unmodifiableList(sorted);
        this.score = score;
    }

    public List<PlacedActivity> getPlacements() {
        return placements;
    }

    public ScheduleScore getScore() {
        return score;
    }

    public int totalTravelMinutes() {
        int total = 0;
        for (PlacedActivity placement : placements) {
            total += placement.getTravelMinutesBefore();
        }
        return total;
    }

    public int totalAvoidableIdleMinutes() {
        int total = 0;
        for (PlacedActivity placement : placements) {
            total += placement.getAvoidableIdleMinutes();
        }
        return total;
    }

    /** Event ids in scheduled order — the readable form used by tests and reasons. */
    public List<String> orderedEventIds() {
        List<String> ids = new ArrayList<>();
        for (PlacedActivity placement : placements) {
            ids.add(placement.getTask().getEventId());
        }
        return Collections.unmodifiableList(ids);
    }

    @Override
    public String toString() {
        return orderedEventIds() + " " + score;
    }
}
