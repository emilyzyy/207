package closeai.application.autoschedule;

import java.time.LocalTime;

/**
 * Named departure periods used to bucket time-dependent travel estimates.
 *
 * <p>Travel time varies with departure time (traffic for driving, timetable
 * frequency for transit), but the underlying providers vary at coarse,
 * period-shaped boundaries: TomTom predicts future departures from historical
 * traffic patterns and Transitous reflects timetable service periods. Named
 * periods therefore capture the real variation while keeping the number of
 * prefetched estimates bounded; uniform 30-minute buckets would imply a
 * precision the providers do not have and multiply requests by an order of
 * magnitude.</p>
 *
 * <p>Boundaries are half-open {@code [start, end)} so that every instant maps to
 * exactly one period, which is what makes bucket lookup deterministic.</p>
 */
public enum DeparturePeriod {
    EARLY(LocalTime.MIDNIGHT, LocalTime.of(11, 0)),
    MIDDAY(LocalTime.of(11, 0), LocalTime.of(16, 0)),
    PEAK(LocalTime.of(16, 0), LocalTime.of(19, 0)),
    LATE(LocalTime.of(19, 0), LocalTime.MAX);

    private final LocalTime start;
    private final LocalTime end;

    DeparturePeriod(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    /** The single period containing {@code time}. Never null. */
    public static DeparturePeriod containing(LocalTime time) {
        if (time == null) {
            throw new IllegalArgumentException("Departure time is required");
        }
        for (DeparturePeriod period : values()) {
            if (!time.isBefore(period.start) && time.isBefore(period.end)) {
                return period;
            }
        }
        return LATE;
    }

    /** A representative departure instant for this period, clamped into {@code window}. */
    public LocalTime sampleWithin(TimeWindow window) {
        LocalTime candidate = start.isBefore(window.getStart()) ? window.getStart() : start;
        if (!candidate.isBefore(window.getEnd())) {
            return window.getStart();
        }
        return candidate;
    }
}
