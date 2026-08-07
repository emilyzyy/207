package closeai.application.autoschedule;

import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builders for readable scheduling fixtures. */
public final class ProblemFixtures {

    public static final java.time.LocalDate TRIP_DATE = java.time.LocalDate.of(2026, 8, 12);

    private ProblemFixtures() {
    }

    public static LocalTime at(int hour, int minute) {
        return LocalTime.of(hour, minute);
    }

    /**
     * An activity whose Location address is its id, so fixtures and the fake estimator
     * can refer to the same route by a short readable name.
     */
    public static Activity activity(String id, LocalTime opening, LocalTime closing) {
        return new Activity(id, id, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, 60, opening, closing,
                IndoorOutdoorType.INDOOR, "none");
    }

    public static Activity activity(String id, ActivityCategory category,
                                    IndoorOutdoorType indoorOutdoor,
                                    LocalTime opening, LocalTime closing) {
        return new Activity(id, id, category, new Location(43.65, -79.38, id), 4.5, 60,
                opening, closing, indoorOutdoor, "none");
    }

    public static ScheduleTask task(String id, int durationMinutes, int originalIndex,
                                    LocalTime opening, LocalTime closing) {
        return ScheduleTask.movable(id, activity(id, opening, closing), durationMinutes, originalIndex);
    }

    public static ScheduleTask lockedTask(String id, int durationMinutes, int originalIndex,
                                          LocalTime opening, LocalTime closing,
                                          LocalTime lockStart) {
        return new ScheduleTask(id, activity(id, opening, closing), durationMinutes, originalIndex,
                new TimeWindow(lockStart, lockStart.plusMinutes(durationMinutes)));
    }

    public static TimeWindow window(int fromHour, int toHour) {
        return new TimeWindow(LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0));
    }

    public static List<TimeWindow> noBlockedWindows() {
        return new ArrayList<>();
    }

    public static List<ScheduleTask> tasks(ScheduleTask... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    /** Builds a matrix where every leg costs the same in every active period. */
    public static TravelMatrix flatMatrix(List<ScheduleTask> tasks, TimeWindow availability,
                                          int minutes) {
        PeriodPlan plan = PeriodPlan.forRun(availability, false,
                tasks.size() * (tasks.size() - 1));
        TravelMatrix.Builder builder = TravelMatrix.builder(plan);
        for (DeparturePeriod period : plan.activePeriods()) {
            for (ScheduleTask from : tasks) {
                for (ScheduleTask to : tasks) {
                    if (!from.getEventId().equals(to.getEventId())) {
                        builder.put(from.getEventId(), to.getEventId(), period,
                                TravelEstimate.routed(minutes));
                    }
                }
            }
        }
        return builder.build();
    }
}
