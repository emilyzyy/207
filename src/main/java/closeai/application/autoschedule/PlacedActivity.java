package closeai.application.autoschedule;

import java.time.LocalTime;

/**
 * One activity fixed at a concrete time, together with the travel leg that preceded it.
 *
 * <p>The travel leg carries its own departure time rather than only a duration, because
 * an unavailable window may force the traveller to set out later than the moment the
 * previous activity ended. The generated travel block is
 * {@code [travelDeparture, travelDeparture + travelMinutes)}.</p>
 */
public final class PlacedActivity {
    private final ScheduleTask task;
    private final LocalTime start;
    private final LocalTime end;
    private final LocalTime travelDeparture;
    private final int travelMinutes;
    private final int idleMinutes;
    private final int avoidableIdleMinutes;

    public PlacedActivity(ScheduleTask task, LocalTime start, LocalTime end,
                          LocalTime travelDeparture, int travelMinutes,
                          int idleMinutes, int avoidableIdleMinutes) {
        this.task = task;
        this.start = start;
        this.end = end;
        this.travelDeparture = travelDeparture;
        this.travelMinutes = travelMinutes;
        this.idleMinutes = idleMinutes;
        this.avoidableIdleMinutes = avoidableIdleMinutes;
    }

    /** A placement with no preceding travel, used for the first activity of the day. */
    public static PlacedActivity first(ScheduleTask task, LocalTime start, LocalTime end,
                                       int idleMinutes, int avoidableIdleMinutes) {
        return new PlacedActivity(task, start, end, null, 0, idleMinutes, avoidableIdleMinutes);
    }

    public ScheduleTask getTask() {
        return task;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    /** When the traveller leaves the previous activity, or null when there is none. */
    public LocalTime getTravelDeparture() {
        return travelDeparture;
    }

    public int getTravelMinutesBefore() {
        return travelMinutes;
    }

    public boolean hasTravel() {
        return travelDeparture != null && travelMinutes > 0;
    }

    /** The travel block to generate, or null when this activity has no preceding leg. */
    public TimeWindow travelWindow() {
        if (!hasTravel()) {
            return null;
        }
        return new TimeWindow(travelDeparture, travelDeparture.plusMinutes(travelMinutes));
    }

    public int getIdleMinutesBefore() {
        return idleMinutes;
    }

    /**
     * Idle that the schedule could in principle have avoided. Waiting for a venue to
     * open, and time inside a period the user declared unavailable, are both excluded:
     * neither is the scheduler's fault, so penalising them would push the search toward
     * worse schedules for no benefit.
     */
    public int getAvoidableIdleMinutes() {
        return avoidableIdleMinutes;
    }

    public TimeWindow window() {
        return new TimeWindow(start, end);
    }

    @Override
    public String toString() {
        return task.getEventId() + "@" + start;
    }
}
