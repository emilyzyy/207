package trippy.application.autoschedule;

import java.time.LocalTime;
import java.util.List;

/**
 * Works out when to set out for the next activity.
 *
 * <p>Leaving the moment the previous activity ends is usually best, but not always
 * possible: an unavailable window is inviolable, so a journey may not run through one.
 * When the direct departure would collide, the traveller waits and leaves once the
 * window has passed. Because travel time itself depends on when the journey starts,
 * a later departure can occasionally arrive sooner, so every sensible departure is
 * considered and the one arriving earliest wins, ties going to the earlier departure.</p>
 */
public final class TravelLegPlanner {

    /**
     * Plans the leg from {@code fromId} to {@code toId} for a traveller free at
     * {@code cursor}.
     *
     * @param notLaterThan latest acceptable arrival, or null when only the day's end applies
     * @return the leg, or null when no departure produces a legal journey
     */
    public TravelLeg plan(TravelMatrix travel, String fromId, String toId, LocalTime cursor,
                          BlockedPeriods blocked, LocalTime notLaterThan) {
        if (fromId == null) {
            return TravelLeg.none(cursor);
        }

        TravelLeg best = null;
        List<LocalTime> departures = blocked.departureOptionsFrom(cursor);
        for (LocalTime departure : departures) {
            if (notLaterThan != null && departure.isAfter(notLaterThan)) {
                break;
            }
            int minutes = travel.estimateAt(fromId, toId, departure).getMinutes();
            LocalTime arrival = departure.plusMinutes(minutes);
            if (!arrival.isAfter(departure) && minutes > 0) {
                continue;
            }
            if (minutes > 0 && blocked.blocks(departure, arrival)) {
                continue;
            }
            if (notLaterThan != null && arrival.isAfter(notLaterThan)) {
                continue;
            }
            if (best == null || arrival.isBefore(best.getArrival())) {
                best = TravelLeg.of(departure, minutes);
            }
        }
        return best;
    }

    /**
     * Minutes of waiting between arriving and starting that the schedule could have
     * avoided. Waiting for the venue to open, and time inside a period the user is
     * unavailable, are both excluded because no ordering of the day could reclaim them.
     */
    public int avoidableIdleMinutes(LocalTime arrival, LocalTime start, LocalTime openingTime,
                                    BlockedPeriods blocked) {
        LocalTime from = arrival.isBefore(openingTime) ? openingTime : arrival;
        if (!start.isAfter(from)) {
            return 0;
        }
        int total = (start.toSecondOfDay() - from.toSecondOfDay()) / 60;
        return Math.max(0, total - blocked.minutesWithin(from, start));
    }
}
