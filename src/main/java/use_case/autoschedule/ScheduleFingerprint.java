package use_case.autoschedule;

import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A summary of the Day Plan a Preview was calculated from.
 *
 * <p>Apply re-reads the trip and compares fingerprints, so a Preview generated before
 * the plan changed cannot be applied over the newer version. Only activity identities
 * and their times are included: regenerated travel blocks are an output of scheduling,
 * not part of what the user chose, so they must not invalidate a Preview.</p>
 *
 * <p>This keeps the safety check inside the use case. The alternative, a version number
 * on the Trip, would mean changing an entity several teammates share.</p>
 */
public final class ScheduleFingerprint {

    private final String value;

    private ScheduleFingerprint(String value) {
        this.value = value;
    }

    public static ScheduleFingerprint of(List<ScheduledEvent> events) {
        List<String> parts = new ArrayList<>();
        for (ScheduledEvent event : events) {
            if (event.getEventType() == EventType.ACTIVITY) {
                parts.add(event.getId() + "@" + event.getStartTime() + "-" + event.getEndTime());
            }
        }
        Collections.sort(parts);
        return new ScheduleFingerprint(String.join(",", parts));
    }

    public static ScheduleFingerprint fromValue(String value) {
        return new ScheduleFingerprint(value == null ? "" : value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScheduleFingerprint)) {
            return false;
        }
        return value.equals(((ScheduleFingerprint) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
