package use_case.autoschedule;

import java.util.Objects;

/** Why one event ended up where it did. */
public final class Reason {
    private final String eventId;
    private final ReasonCode code;
    private final String detail;

    public Reason(String eventId, ReasonCode code, String detail) {
        if (eventId == null || code == null) {
            throw new IllegalArgumentException("Reason needs an event and a code");
        }
        this.eventId = eventId;
        this.code = code;
        this.detail = detail == null ? "" : detail;
    }

    public String getEventId() {
        return eventId;
    }

    public ReasonCode getCode() {
        return code;
    }
    /**
     * Optional supporting fact, such as a closing time. Never a full sentence.
     * @return the result of the operation
     */

    public String getDetail() {
        return detail;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Reason)) {
            return false;
        }
        final Reason that = (Reason) other;
        return eventId.equals(that.eventId) && code == that.code && detail.equals(that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, code, detail);
    }

    @Override
    public String toString() {
        return eventId + ":" + code;
    }
}
