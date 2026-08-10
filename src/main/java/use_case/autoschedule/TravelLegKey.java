package use_case.autoschedule;

import java.time.LocalTime;
import java.util.Objects;

/** Identifies one directed leg leaving at a specific time, for refinement overrides. */
public final class TravelLegKey {
    private final String fromId;
    private final String toId;
    private final LocalTime departure;

    public TravelLegKey(String fromId, String toId, LocalTime departure) {
        if (fromId == null || toId == null || departure == null) {
            throw new IllegalArgumentException("Travel leg key is incomplete");
        }
        this.fromId = fromId;
        this.toId = toId;
        this.departure = departure;
    }

    public String getFromId() {
        return fromId;
    }

    public String getToId() {
        return toId;
    }

    public LocalTime getDeparture() {
        return departure;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TravelLegKey)) {
            return false;
        }
        final TravelLegKey that = (TravelLegKey) other;
        return fromId.equals(that.fromId) && toId.equals(that.toId)
                && departure.equals(that.departure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromId, toId, departure);
    }
}
