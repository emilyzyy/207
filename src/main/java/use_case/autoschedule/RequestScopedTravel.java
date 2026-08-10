package use_case.autoschedule;

import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One Autoschedule request's view of the routing provider, held still for its lifetime.
 *
 * <p>A request asks for estimates twice: once in bulk to search with, and again for the exact
 * departure times of the schedule it chose. Those are two conversations with a live service,
 * and between them traffic predictions move. A request could therefore search against one set
 * of numbers and validate against another, and the same day pressed twice could be feasible
 * once and not the next time with nothing on screen having changed.</p>
 *
 * <p>Every leg asked for is answered once and remembered, so a request is internally coherent
 * whatever the world does while it runs. Separate requests still see the world afresh — the
 * point is not to freeze routing, it is to stop one request mixing two versions of it.</p>
 *
 * <p>A provider failure is remembered as a failure and reported, rather than being retried into
 * a different answer halfway through. {@link #getDiagnostics()} lists what was actually used,
 * so genuine variation between two requests can be explained rather than guessed at.</p>
 */
public final class RequestScopedTravel implements TravelTimeEstimator {

    private final TravelTimeEstimator source;
    private final Map<String, TravelEstimate> answered = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();

    public RequestScopedTravel(TravelTimeEstimator source) {
        if (source == null) {
            throw new IllegalArgumentException("A travel estimator is required");
        }
        this.source = source;
    }

    @Override
    public TravelEstimate estimate(Location from, Location to, TransportationMode mode,
                                   LocalDateTime departure) {
        String key = keyFor(from, to, mode, departure);
        TravelEstimate remembered = answered.get(key);
        if (remembered != null) {
            return remembered;
        }
        TravelEstimate answer = source.estimate(from, to, mode, departure);
        if (answer != null) {
            answered.put(key, answer);
        }
        return answer;
    }

    @Override
    public boolean isTimeSensitive(TransportationMode mode) {
        return source.isTimeSensitive(mode);
    }

    /** Records a leg the provider refused to answer, so the reason survives the request. */
    public void recordFailure(String description) {
        failures.add(description);
    }

    /**
     * Every estimate this request used, and every leg it could not get one for.
     *
     * <p>The point of comparison when two attempts on an unchanged day disagree: if the
     * schedules differ, these lines say whether the provider did.</p>
     */
    public List<String> getDiagnostics() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, TravelEstimate> entry : answered.entrySet()) {
            lines.add(entry.getKey() + " = " + entry.getValue().getMinutes() + " min ("
                    + entry.getValue().getQuality() + ")");
        }
        lines.addAll(failures);
        return lines;
    }

    private static String keyFor(Location from, Location to, TransportationMode mode,
                                 LocalDateTime departure) {
        return from.getLatitude() + "," + from.getLongitude() + ">"
                + to.getLatitude() + "," + to.getLongitude() + "@" + mode + "@" + departure;
    }
}
