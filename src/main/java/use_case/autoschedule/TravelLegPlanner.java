package use_case.autoschedule;

import java.time.LocalTime;
import java.util.ArrayList;
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
     * The latest departure that still reaches {@code arriveBy}, given the traveller is free
     * from {@code cursor}.
     *
     * <p>{@link #plan} answers "how early could I get there?", which is the question
     * feasibility turns on. It is the wrong question for the journey the traveller actually
     * makes. When a venue opens at 11:30 and the hop takes seven minutes, leaving at 10:00
     * buys nothing: it converts an hour and a half of free time near the previous activity
     * into an hour and a half of standing outside a shut door, and the schedule showed
     * exactly that — a journey drawn at 10:00 for an activity starting at 11:30.</p>
     *
     * <p>So the arrival is fixed by {@link #plan} and the departure is then slid as late as
     * it will go. Travel time is itself departure-dependent, so a later departure can take
     * longer and miss the arrival; each period offers one candidate departure, the one that
     * lands exactly on {@code arriveBy} at that period's cost, and only candidates that
     * really fall inside their own period are eligible. Unavailable windows are re-checked,
     * because sliding a journey later can push it into one — and when one sits across the
     * journey, arriving just before it is offered as a second candidate, so a traveller busy
     * from two until three still travels at ten to two rather than at half past one.</p>
     *
     * @param earliest the feasibility leg, returned unchanged when nothing later works
     * @return the leg to actually travel; never null when {@code earliest} is non-null
     */
    public TravelLeg latestArrivingBy(TravelMatrix travel, String fromId, String toId,
                                      LocalTime cursor, BlockedPeriods blocked,
                                      LocalTime arriveBy, TravelLeg earliest) {
        if (earliest == null || fromId == null || arriveBy == null) {
            return earliest;
        }
        // Landing exactly on the start is ideal, but an unavailable window can sit across
        // that journey — the traveller is busy from two until three and the activity begins
        // at three. Arriving just before such a window is the next best thing and still far
        // later than setting out at the first opportunity, so each one offers its own
        // candidate arrival.
        List<LocalTime> arrivals = new ArrayList<>();
        arrivals.add(arriveBy);
        for (TimeWindow window : blocked.getWindows()) {
            if (window.getStart().isAfter(cursor) && !window.getStart().isAfter(arriveBy)) {
                arrivals.add(window.getStart());
            }
        }

        TravelLeg best = earliest;
        for (LocalTime arrival : arrivals) {
            for (DeparturePeriod period : DeparturePeriod.values()) {
                int minutes = travel.estimateAt(fromId, toId, period.getStart()).getMinutes();
                LocalTime departure = arrival.minusMinutes(minutes);
                if (minutes > 0 && !departure.isBefore(arrival)) {
                    continue;
                }
                if (departure.isBefore(cursor) || !departure.isAfter(best.getDeparture())) {
                    continue;
                }
                // The cost used to place this departure has to be the cost of departing then,
                // or the leg would be priced from one period and travelled in another.
                if (travel.estimateAt(fromId, toId, departure).getMinutes() != minutes) {
                    continue;
                }
                if (minutes > 0 && blocked.blocks(departure, departure.plusMinutes(minutes))) {
                    continue;
                }
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
