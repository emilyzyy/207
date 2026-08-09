package use_case.autoschedule;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.OpeningHours;
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

    /**
     * Real per-weekday hours, written the way a test wants to read them.
     *
     * <p>Built here rather than through the OSM parser on purpose: this is the application
     * layer, and it must be provable that the scheduler obeys normalised hours regardless of
     * which provider produced them.</p>
     *
     * @param spans {@code "09:00-12:00", "13:00-17:00"} and so on, all on the given weekday
     */
    public static OpeningHours hoursOn(java.time.DayOfWeek day, String... spans) {
        List<OpeningHours.TimeInterval> intervals = new ArrayList<>();
        for (String span : spans) {
            String[] halves = span.split("-");
            intervals.add(new OpeningHours.TimeInterval(
                    LocalTime.parse(halves[0]), LocalTime.parse(halves[1])));
        }
        java.util.Map<java.time.DayOfWeek, List<OpeningHours.TimeInterval>> week =
                new java.util.EnumMap<>(java.time.DayOfWeek.class);
        week.put(day, intervals);
        return OpeningHours.of(week);
    }

    /** An activity carrying real hours, with a deliberately wide fallback window behind them. */
    public static Activity activityWithHours(String id, OpeningHours hours) {
        return new Activity(id, id, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(0, 0), LocalTime.of(23, 59),
                IndoorOutdoorType.INDOOR, "none", null, hours);
    }

    /** A movable task whose hours are the real ones for {@link #TRIP_DATE}. */
    public static ScheduleTask taskWithHours(String id, int durationMinutes, int originalIndex,
                                             OpeningHours hours) {
        return new ScheduleTask(id, activityWithHours(id, hours), durationMinutes,
                originalIndex, null, TRIP_DATE);
    }

    /** A pinned task whose hours are the real ones for {@link #TRIP_DATE}. */
    public static ScheduleTask lockedTaskWithHours(String id, int durationMinutes,
                                                   int originalIndex, OpeningHours hours,
                                                   LocalTime lockStart) {
        return new ScheduleTask(id, activityWithHours(id, hours), durationMinutes, originalIndex,
                new TimeWindow(lockStart, lockStart.plusMinutes(durationMinutes)), TRIP_DATE);
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
