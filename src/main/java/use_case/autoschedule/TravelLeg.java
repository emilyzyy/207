package use_case.autoschedule;

import java.time.LocalTime;

/**
 * A planned journey between two activities: when the traveller leaves, how long it
 * takes at that departure time, and when they arrive.
 */
public final class TravelLeg {
    private static final TravelLeg NONE = new TravelLeg(null, 0, null);

    private final LocalTime departure;
    private final int minutes;
    private final LocalTime arrival;

    private TravelLeg(LocalTime departure, int minutes, LocalTime arrival) {
        this.departure = departure;
        this.minutes = minutes;
        this.arrival = arrival;
    }

    public static TravelLeg of(LocalTime departure, int minutes) {
        return new TravelLeg(departure, minutes, departure.plusMinutes(minutes));
    }

    /** No travel at all, for the first activity of the day. */
    public static TravelLeg none(LocalTime at) {
        return new TravelLeg(null, 0, at);
    }

    public LocalTime getDeparture() {
        return departure;
    }

    public int getMinutes() {
        return minutes;
    }

    public LocalTime getArrival() {
        return arrival;
    }

    public boolean isTravelled() {
        return departure != null && minutes > 0;
    }

    /** The window this leg would occupy, or null when nothing is travelled. */
    public TimeWindow window() {
        return isTravelled() ? new TimeWindow(departure, arrival) : null;
    }

    @Override
    public String toString() {
        return isTravelled() ? departure + "+" + minutes + "min" : "no travel";
    }
}
