package use_case.autoschedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The immutable scheduling problem handed to the engine: what must be placed, when
 * the user is available, what is blocked, and what travel costs between activities.
 *
 * <p>Everything the search needs is resolved here, so the search itself is a pure
 * function with no repository, network or Swing dependency.</p>
 */
public final class ScheduleProblem {
    private final TimeWindow availability;
    private final List<ScheduleTask> movableTasks;
    private final List<ScheduleTask> lockedTasks;
    private final List<TimeWindow> unavailableWindows;
    private final TravelMatrix travel;
    private final SchedulingPreferences preferences;

    public ScheduleProblem(TimeWindow availability, List<ScheduleTask> tasks,
                           List<TimeWindow> unavailableWindows, TravelMatrix travel) {
        this(availability, tasks, unavailableWindows, travel, SchedulingPreferences.none());
    }

    public ScheduleProblem(TimeWindow availability, List<ScheduleTask> tasks,
                           List<TimeWindow> unavailableWindows, TravelMatrix travel,
                           SchedulingPreferences preferences) {
        if (availability == null) {
            throw new IllegalArgumentException("Availability window is required");
        }
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Add activities to the Day Plan before scheduling");
        }
        if (travel == null) {
            throw new IllegalArgumentException("Travel estimates are required");
        }

        final List<ScheduleTask> movable = new ArrayList<>();
        final List<ScheduleTask> locked = new ArrayList<>();
        final Set<String> seenIds = new HashSet<>();
        for (ScheduleTask task : tasks) {
            if (task == null) {
                throw new IllegalArgumentException("Schedule tasks cannot be null");
            }
            if (!seenIds.add(task.getEventId())) {
                throw new IllegalArgumentException("Duplicate task id: " + task.getEventId());
            }
            if (task.isLocked()) {
                locked.add(task);
            }
            else {
                movable.add(task);
            }
        }

        this.availability = availability;
        this.movableTasks = Collections.unmodifiableList(movable);
        this.lockedTasks = Collections.unmodifiableList(locked);
        this.unavailableWindows = Collections.unmodifiableList(new ArrayList<>(
                unavailableWindows == null ? Collections.<TimeWindow>emptyList() : unavailableWindows));
        this.travel = travel;
        this.preferences = preferences == null ? SchedulingPreferences.none() : preferences;
    }

    public SchedulingPreferences getPreferences() {
        return preferences;
    }
    /**
     * Periods in which nothing may be scheduled, from the traveller's point of view at
     * {@code lockedIndex} locks into the day. Unavailable windows are always included;
     * locks still ahead are added because a traveller cannot be journeying through an
     * appointment they have already committed to.
      * @param locksInOrder the l oc ks in or de r value
      * @param lockedIndex the l oc ke di nd ex value
      * @return the result of the operation
     */

    public BlockedPeriods blockedPeriodsFrom(List<ScheduleTask> locksInOrder, int lockedIndex) {
        BlockedPeriods blocked = BlockedPeriods.of(unavailableWindows);
        for (int i = lockedIndex; i < locksInOrder.size(); i++) {
            blocked = blocked.plus(locksInOrder.get(i).getLockedAt());
        }
        return blocked;
    }

    public TimeWindow getAvailability() {
        return availability;
    }

    public List<ScheduleTask> getMovableTasks() {
        return movableTasks;
    }

    public List<ScheduleTask> getLockedTasks() {
        return lockedTasks;
    }

    public List<TimeWindow> getUnavailableWindows() {
        return unavailableWindows;
    }

    public TravelMatrix getTravel() {
        return travel;
    }

    /**
     * Performs the t as kc ou nt operation.
     * @return the result of the operation
     */
    public int taskCount() {
        return movableTasks.size() + lockedTasks.size();
    }
    /**
     * All tasks, locked first, in a deterministic order.
     * @return the result of the operation
     */

    public List<ScheduleTask> allTasks() {
        final List<ScheduleTask> all = new ArrayList<>(lockedTasks);
        all.addAll(movableTasks);
        return Collections.unmodifiableList(all);
    }
}
