package trippy.application.autoschedule.testdoubles;

import trippy.application.autoschedule.DeparturePeriod;
import trippy.application.autoschedule.TravelEstimate;
import trippy.application.autoschedule.TravelTimeEstimator;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic travel estimator for tests, programmable per departure period so the
 * same route can legitimately cost different amounts at different times of day.
 * Records every call so tests can assert how many requests a run would make.
 */
public final class FakeTravelTimeEstimator implements TravelTimeEstimator {

    private final Map<String, Integer> byRouteAndPeriod = new HashMap<>();
    private final Map<String, Integer> byRoute = new HashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private int defaultMinutes = 10;
    private boolean timeSensitive = true;

    /** Registers a duration used for this route in every period. */
    public FakeTravelTimeEstimator route(String fromId, String toId, int minutes) {
        byRoute.put(key(fromId, toId), minutes);
        return this;
    }

    /** Registers a duration used for this route only when departing in {@code period}. */
    public FakeTravelTimeEstimator route(String fromId, String toId,
                                         DeparturePeriod period, int minutes) {
        byRouteAndPeriod.put(key(fromId, toId) + period.name(), minutes);
        return this;
    }

    public FakeTravelTimeEstimator defaultMinutes(int minutes) {
        this.defaultMinutes = minutes;
        return this;
    }

    public FakeTravelTimeEstimator timeSensitive(boolean value) {
        this.timeSensitive = value;
        return this;
    }

    @Override
    public TravelEstimate estimate(Location from, Location to,
                                   TransportationMode mode, LocalDateTime departure) {
        String fromId = from.getAddress();
        String toId = to.getAddress();
        DeparturePeriod period = DeparturePeriod.containing(departure.toLocalTime());
        calls.add(new Call(fromId, toId, period));

        Integer perPeriod = byRouteAndPeriod.get(key(fromId, toId) + period.name());
        if (perPeriod != null) {
            return TravelEstimate.routed(perPeriod);
        }
        Integer flat = byRoute.get(key(fromId, toId));
        return TravelEstimate.routed(flat == null ? defaultMinutes : flat);
    }

    @Override
    public boolean isTimeSensitive(TransportationMode mode) {
        return timeSensitive;
    }

    public int callCount() {
        return calls.size();
    }

    public List<Call> getCalls() {
        return calls;
    }

    public void resetCalls() {
        calls.clear();
    }

    private static String key(String fromId, String toId) {
        return fromId + ">" + toId + "@";
    }

    /** One recorded estimate request. */
    public static final class Call {
        private final String fromId;
        private final String toId;
        private final DeparturePeriod period;

        Call(String fromId, String toId, DeparturePeriod period) {
            this.fromId = fromId;
            this.toId = toId;
            this.period = period;
        }

        public String getFromId() {
            return fromId;
        }

        public String getToId() {
            return toId;
        }

        public DeparturePeriod getPeriod() {
            return period;
        }
    }
}
