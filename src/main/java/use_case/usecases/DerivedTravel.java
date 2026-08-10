package use_case.usecases;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import entity.valueobjects.TransportationMode;
import use_case.ports.DistanceService;

/**
 * What happens to generated travel when the traveller changes the day by hand.
 *
 * <p>Travel is derived from the <em>sequence</em> of activities, not from any one of them. So
 * removing an activity invalidates exactly two journeys — the one into it and the one out of
 * it — and creates one new adjacency in their place. Every other leg in the day is between the
 * same two activities it always was and is still true.</p>
 *
 * <p>The previous rule dropped every travel block on any hand edit. That was safe and wrong:
 * removing one activity from an applied four-activity day erased the journeys between the
 * three that remained, leaving a plan that looked as though nothing was more than a step from
 * anything else until Autoschedule was run again.</p>
 *
 * <p>A travel event's identifier is {@code travel-<destination event id>}, which is what makes
 * "the leg into D" findable without storing a second copy of the day's structure.</p>
 */
public final class DerivedTravel {

    /** Prefix Autoschedule uses when it materialises a journey; see the class note. */
    static final String TRAVEL_ID_PREFIX = "travel-";

    private DerivedTravel() {

    }
    /**
     * The day after {@code removedEventId} goes, with its journeys repaired.
     *
     * <p>Legs between pairs that are still adjacent are kept exactly as they were, including
     * the times Autoschedule chose for them. The one pair that has become newly adjacent gets
     * a freshly estimated leg, timed to arrive just as the next activity begins, which is the
     * same rule the scheduler itself follows.</p>
     *
     * <p>When the estimate cannot be obtained, or the gap between the two activities is too
     * small to hold the journey, no leg is invented: the pair is simply left without one. That
     * is a visible absence rather than a fiction, and it is recoverable by running
     * Autoschedule again.</p>
     *
      * @param events the e ve nt s value
     * @param distances may be null, in which case no replacement leg is computed
      * @return the result of the operation
     */

    public static List<ScheduledEvent> afterRemoving(List<ScheduledEvent> events,
                                                     String removedEventId,
                                                     TransportationMode mode,
                                                     LocalDate date,
                                                     DistanceService distances) {
        final List<ScheduledEvent> before = activitiesOf(events);
        final Map<String, ScheduledEvent> legsByDestination = travelByDestination(events);

        final List<ScheduledEvent> after = new ArrayList<>();
        for (ScheduledEvent activity : before) {
            if (!activity.getId().equals(removedEventId)) {
                after.add(activity);
            }
        }

        final List<ScheduledEvent> rebuilt = new ArrayList<>();
        for (int i = 0; i < after.size(); i++) {
            final ScheduledEvent destination = after.get(i);
            if (i > 0) {
                final ScheduledEvent arrivingFrom = after.get(i - 1);
                final ScheduledEvent leg = legFor(arrivingFrom, destination, before,
                        legsByDestination, mode, date, distances);
                if (leg != null) {
                    rebuilt.add(leg);
                }
            }
            rebuilt.add(destination);
        }
        return rebuilt;
    }

    /**
     * The journey into {@code destination}, kept when the pair is unchanged and re-estimated
     * when the removal made them newly adjacent.
      * @param destination the d es ti na ti on value
      * @param arrivingFrom the a rr iv in gf ro m value
      * @return the result of the operation
     */
    private static ScheduledEvent legFor(ScheduledEvent arrivingFrom, ScheduledEvent destination,
                                         List<ScheduledEvent> before,
                                         Map<String, ScheduledEvent> legsByDestination,
                                         TransportationMode mode, LocalDate date,
                                         DistanceService distances) {
        final ScheduledEvent existing = legsByDestination.get(destination.getId());
        if (existing != null && wasAlreadyAdjacent(arrivingFrom, destination, before)) {
            return existing;
        }
        return estimatedLeg(arrivingFrom, destination, mode, date, distances);
    }

    /**
     * Whether these two ran back to back before anything was removed.
     * @param arrivingFrom the a rr iv in gf ro m value
     * @return the result of the operation
     */
    private static boolean wasAlreadyAdjacent(ScheduledEvent arrivingFrom,
                                              ScheduledEvent destination,
                                              List<ScheduledEvent> before) {
        for (int i = 1; i < before.size(); i++) {
            if (before.get(i).getId().equals(destination.getId())) {
                return before.get(i - 1).getId().equals(arrivingFrom.getId());
            }
        }
        return false;
    }

    /**
     * A journey timed to land exactly as the destination begins.
     *
     * <p>Null when it cannot honestly be drawn: no estimator, no coordinates, a provider that
     * failed, a journey of no length, or a gap too short to contain it. Returning null loses a
     * leg; returning something else would lose the truth.</p>
      * @param arrivingFrom the a rr iv in gf ro m value
      * @return the result of the operation
     */
    private static ScheduledEvent estimatedLeg(ScheduledEvent arrivingFrom,
                                               ScheduledEvent destination,
                                               TransportationMode mode, LocalDate date,
                                               DistanceService distances) {
        final Activity from = arrivingFrom.getActivity();
        final Activity to = destination.getActivity();
        if (distances == null || from == null || to == null || mode == null || date == null) {
            return null;
        }
        final int minutes;
        try {
            minutes = distances.estimateTravelMinutes(from.getLocation(), to.getLocation(), mode,
                    LocalDateTime.of(date, arrivingFrom.getEndTime()));
        }
        catch (RuntimeException providerFailed) {
            // The day still saves; it simply has one fewer drawn journey than it might.
            return null;
        }
        if (minutes <= 0) {
            return null;
        }
        final int gap = (destination.getStartTime().toSecondOfDay()
                - arrivingFrom.getEndTime().toSecondOfDay()) / 60;
        if (gap < minutes) {
            return null;
        }
        final LocalTime departure = destination.getStartTime().minusMinutes(minutes);
        return new ScheduledEvent(TRAVEL_ID_PREFIX + destination.getId(), null, departure,
                destination.getStartTime(), EventType.TRAVEL,
                "Travel to " + to.getName());
    }

    private static List<ScheduledEvent> activitiesOf(List<ScheduledEvent> events) {
        final List<ScheduledEvent> activities = new ArrayList<>();
        if (events == null) {
            return activities;
        }
        for (ScheduledEvent event : events) {
            if (event.getEventType() != EventType.TRAVEL) {
                activities.add(event);
            }
        }
        return activities;
    }

    private static Map<String, ScheduledEvent> travelByDestination(List<ScheduledEvent> events) {
        final Map<String, ScheduledEvent> legs = new LinkedHashMap<>();
        if (events == null) {
            return legs;
        }
        for (ScheduledEvent event : events) {
            if (event.getEventType() == EventType.TRAVEL
                    && event.getId().startsWith(TRAVEL_ID_PREFIX)) {
                legs.put(event.getId().substring(TRAVEL_ID_PREFIX.length()), event);
            }
        }
        return legs;
    }
}
