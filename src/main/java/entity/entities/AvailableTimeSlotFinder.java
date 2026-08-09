package entity.entities;

import entity.entities.ScheduledEvent;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds the earliest useful quarter-hour gap in an existing day plan. */
public final class AvailableTimeSlotFinder {
    private static final int[] PREFERRED_DURATIONS = {60, 45, 30, 15};

    public Slot find(LocalTime dayStart, LocalTime dayEnd, List<ScheduledEvent> events) {
        List<Gap> gaps = gaps(dayStart, dayEnd, events);
        for (int duration : PREFERRED_DURATIONS) {
            for (Gap gap : gaps) {
                LocalTime alignedStart = ceilToQuarter(gap.start);
                if (!alignedStart.plusMinutes(duration).isAfter(gap.end)) {
                    return new Slot(alignedStart, alignedStart.plusMinutes(duration));
                }
            }
        }
        for (Gap gap : gaps) {
            LocalTime alignedStart = ceilToQuarter(gap.start);
            long available = Duration.between(alignedStart, gap.end).toMinutes();
            int quarterMinutes = (int) (available / 15) * 15;
            if (quarterMinutes > 0) {
                return new Slot(alignedStart, alignedStart.plusMinutes(quarterMinutes));
            }
        }
        return null;
    }

    private List<Gap> gaps(LocalTime dayStart, LocalTime dayEnd,
                           List<ScheduledEvent> events) {
        List<ScheduledEvent> ordered = new ArrayList<>(
                events == null ? java.util.Collections.emptyList() : events);
        ordered.sort(Comparator.comparing(ScheduledEvent::getStartTime));
        List<Gap> gaps = new ArrayList<>();
        LocalTime cursor = dayStart;
        for (ScheduledEvent event : ordered) {
            LocalTime start = event.getStartTime().isBefore(dayStart)
                    ? dayStart : event.getStartTime();
            LocalTime end = event.getEndTime().isAfter(dayEnd)
                    ? dayEnd : event.getEndTime();
            if (start.isAfter(cursor)) gaps.add(new Gap(cursor, start));
            if (end.isAfter(cursor)) cursor = end;
        }
        if (dayEnd.isAfter(cursor)) gaps.add(new Gap(cursor, dayEnd));
        return gaps;
    }

    private static LocalTime ceilToQuarter(LocalTime time) {
        int minutes = time.getHour() * 60 + time.getMinute();
        int aligned = ((minutes + 14) / 15) * 15;
        return LocalTime.of((aligned / 60) % 24, aligned % 60);
    }

    private static final class Gap {
        private final LocalTime start;
        private final LocalTime end;

        private Gap(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }
    }

    public static final class Slot {
        private final LocalTime start;
        private final LocalTime end;

        private Slot(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }

        public LocalTime getStart() { return start; }
        public LocalTime getEnd() { return end; }
    }
}
