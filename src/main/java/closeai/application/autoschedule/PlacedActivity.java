package closeai.application.autoschedule;

import java.time.LocalTime;

/** One activity fixed at a concrete time, with the travel that preceded it. */
public final class PlacedActivity {
    private final ScheduleTask task;
    private final LocalTime start;
    private final LocalTime end;
    private final int travelMinutesBefore;
    private final int idleMinutesBefore;
    private final int unavoidableWaitMinutes;

    public PlacedActivity(ScheduleTask task, LocalTime start, LocalTime end,
                          int travelMinutesBefore, int idleMinutesBefore,
                          int unavoidableWaitMinutes) {
        this.task = task;
        this.start = start;
        this.end = end;
        this.travelMinutesBefore = travelMinutesBefore;
        this.idleMinutesBefore = idleMinutesBefore;
        this.unavoidableWaitMinutes = unavoidableWaitMinutes;
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

    public int getTravelMinutesBefore() {
        return travelMinutesBefore;
    }

    public int getIdleMinutesBefore() {
        return idleMinutesBefore;
    }

    /** Idle that could not be avoided because the venue had not opened yet. */
    public int getUnavoidableWaitMinutes() {
        return unavoidableWaitMinutes;
    }

    public int getAvoidableIdleMinutes() {
        return Math.max(0, idleMinutesBefore - unavoidableWaitMinutes);
    }

    public TimeWindow window() {
        return new TimeWindow(start, end);
    }

    @Override
    public String toString() {
        return task.getEventId() + "@" + start;
    }
}
