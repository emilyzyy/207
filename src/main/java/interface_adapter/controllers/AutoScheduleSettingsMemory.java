package interface_adapter.controllers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the traveller last told Autoschedule, remembered for as long as the app is open.
 *
 * <p>Running Autoschedule twice on the same day usually means running it with the same
 * constraints. Re-entering "unavailable 10:00 AM to 1:00 PM" before every attempt is the kind
 * of friction that makes a feature feel hostile.</p>
 *
 * <p>Deliberately <em>not</em> in the trip repository. These are one person's working
 * preferences while they fiddle with a plan, not a commitment the whole party has agreed to;
 * writing them to the shared itinerary would let one roommate's temporary "I'm busy at noon"
 * silently reshape everybody else's schedule. They live in memory, keyed by the day being
 * planned, and die with the session.</p>
 *
 * <p>Nothing here is applied invisibly. The settings dialog fills its controls from this and
 * the traveller can see, change or clear every remembered value before the next run — a
 * constraint that affects the answer while hidden is worse than one that was never
 * remembered.</p>
 */
public final class AutoScheduleSettingsMemory {

    private final Map<String, AutoScheduleSettings> byDay = new LinkedHashMap<>();

    /** Key on the day, not just the trip: a multi-day trip is several separate plans. */
    private static String keyFor(String tripId, int dayIndex) {
        return (tripId == null ? "" : tripId) + "#" + dayIndex;
    }

    /** What was used last time for this day, or null when it has not been scheduled yet. */
    public AutoScheduleSettings remembered(String tripId, int dayIndex) {
        return byDay.get(keyFor(tripId, dayIndex));
    }

    public void remember(String tripId, int dayIndex, AutoScheduleSettings settings) {
        if (settings == null) {
            return;
        }
        byDay.put(keyFor(tripId, dayIndex), settings);
    }

    /** Explicit reset, for the traveller who wants a clean start rather than a tidy-up. */
    public void forget(String tripId, int dayIndex) {
        byDay.remove(keyFor(tripId, dayIndex));
    }

    public void forgetEverything() {
        byDay.clear();
    }
}
