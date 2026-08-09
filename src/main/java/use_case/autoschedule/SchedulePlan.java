package use_case.autoschedule;

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

    /**
     * All the waiting in the day, including waiting nothing could have avoided.
     *
     * <p>This is what the Preview reports, because it is what the timeline draws. The
     * ranking uses {@link #totalAvoidableIdleMinutes()} instead, which is the right measure
     * for choosing between orders and the wrong one for describing a day: reporting it made
     * the Preview claim zero waiting while an eighty-three minute hole sat on screen, and
     * announce "289 min of waiting removed" against a figure that had never counted the same
     * thing.</p>
     */
    public int totalIdleMinutes() {
        int total = 0;
        // From the second activity onwards. Time before the day's first activity is not
        // waiting, it is a day that starts later, and the "before" figure has never counted
        // it either — including it here reported three hours of waiting for a day whose
        // timeline had no gap in it at all.
        for (int position = 1; position < placements.size(); position++) {
            total += placements.get(position).getIdleMinutesBefore();
        }
        return total;
    }

    /** Waiting a different order could in principle have reclaimed. Used for ranking. */
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
