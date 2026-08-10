package use_case.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import use_case.ports.DistanceService;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Removing an activity from a day Autoschedule has already applied.
 *
 * <p>Reported from the running application: apply a proposal, remove one activity, and every
 * generated journey vanishes — including the ones between activities that are still there. The
 * day is left looking as though nothing is more than a step from anything else.</p>
 *
 * <p>Travel is derived from the sequence, so exactly two legs were ever invalidated: into the
 * removed activity and out of it. Those two become one new adjacency, estimated afresh at the
 * transport mode the trip is actually planned in.</p>
 */
class AppliedPlanRemovalTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    /** Deterministic, and records what it was asked, so the mode can be checked. */
    private static final class RecordingDistances implements DistanceService {
        private final List<TransportationMode> modesAsked = new ArrayList<>();
        private final int minutes;
        private boolean broken;

        private RecordingDistances(int minutes) {
            this.minutes = minutes;
        }

        @Override
        public int estimateTravelMinutes(Location from, Location to, TransportationMode mode,
                                         LocalDateTime departure) {
            if (broken) {
                throw new IllegalStateException("routing provider is down");
            }
            modesAsked.add(mode);
            return minutes;
        }
    }

    private static Activity place(String id) {
        return new Activity(id, "Place " + id, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(8, 0), LocalTime.of(21, 0), IndoorOutdoorType.INDOOR, "none");
    }

    /**
     * A day in the shape Apply leaves behind: activities with generated journeys between them,
     * each identified as {@code travel-<destination>}.
     */
    private static Trip appliedDay(int activityCount, TransportationMode mode) {
        List<ScheduledEvent> events = new ArrayList<>();
        LocalTime cursor = LocalTime.of(9, 0);
        for (int i = 0; i < activityCount; i++) {
            String id = "e" + i;
            if (i > 0) {
                events.add(new ScheduledEvent("travel-" + id, null, cursor,
                        cursor.plusMinutes(15), EventType.TRAVEL, "Travel to Place " + id));
                cursor = cursor.plusMinutes(15);
            }
            events.add(new ScheduledEvent(id, place(id), cursor, cursor.plusMinutes(60),
                    EventType.ACTIVITY, ""));
            cursor = cursor.plusMinutes(60 + 45);
        }
        Trip trip = new Trip("trip-1", "Toronto", DATE, LocalTime.of(9, 0), LocalTime.of(21, 0),
                mode);
        trip.replaceSchedule(events);
        return trip;
    }

    private static List<String> idsOf(Trip trip, EventType type) {
        List<String> ids = new ArrayList<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getEventType() == type) {
                ids.add(event.getId());
            }
        }
        return ids;
    }

    private static String describe(Trip trip) {
        StringBuilder text = new StringBuilder("\n");
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            text.append("  ").append(event.getEventType()).append(' ')
                    .append(event.getStartTime()).append('-').append(event.getEndTime())
                    .append(' ').append(event.getId()).append('\n');
        }
        return text.toString();
    }

    /** No leg may point at an activity that has gone, and none may be drawn twice. */
    private static void assertTravelIsSoundlyConnected(Trip trip) {
        Set<String> activities = new HashSet<>(idsOf(trip, EventType.ACTIVITY));
        Set<String> destinations = new HashSet<>();
        for (String legId : idsOf(trip, EventType.TRAVEL)) {
            String destination = legId.replaceFirst("^travel-", "");
            assertTrue(activities.contains(destination),
                    "orphaned journey " + legId + describe(trip));
            assertTrue(destinations.add(destination),
                    "duplicate journey " + legId + describe(trip));
        }
    }

    private static Trip remove(Trip trip, String eventId, DistanceService distances) {
        FakeTripRepository trips = new FakeTripRepository(trip);
        return new RemoveScheduledEventUseCase(trips, distances).execute("trip-1", eventId);
    }

    // 1
    @Test
    void removingTheFirstActivityKeepsTheJourneysBetweenTheRest() {
        Trip after = remove(appliedDay(4, TransportationMode.WALKING), "e0",
                new RecordingDistances(12));

        assertEquals(List.of("e1", "e2", "e3"), idsOf(after, EventType.ACTIVITY));
        assertEquals(2, idsOf(after, EventType.TRAVEL).size(),
                "three activities in a row need two journeys" + describe(after));
        assertTravelIsSoundlyConnected(after);
    }

    // 2
    @Test
    void removingAMiddleActivityReplacesTwoJourneysWithOne() {
        Trip before = appliedDay(4, TransportationMode.WALKING);
        Trip after = remove(before, "e2", new RecordingDistances(12));

        assertEquals(List.of("e0", "e1", "e3"), idsOf(after, EventType.ACTIVITY));
        List<String> legs = idsOf(after, EventType.TRAVEL);
        assertEquals(2, legs.size(), "A-B survives and B-D is new" + describe(after));
        assertTrue(legs.contains("travel-e1"), "the journey into B is unchanged" + describe(after));
        assertTrue(legs.contains("travel-e3"), "and B to D is drawn" + describe(after));
        assertFalse(legs.contains("travel-e2"), "the journey to the removed activity is gone");
        assertTravelIsSoundlyConnected(after);
    }

    // 3
    @Test
    void removingTheFinalActivityDropsOnlyItsIncomingJourney() {
        Trip after = remove(appliedDay(4, TransportationMode.WALKING), "e3",
                new RecordingDistances(12));

        assertEquals(List.of("e0", "e1", "e2"), idsOf(after, EventType.ACTIVITY));
        assertEquals(List.of("travel-e1", "travel-e2"), idsOf(after, EventType.TRAVEL),
                describe(after));
        assertTravelIsSoundlyConnected(after);
    }

    // 4
    @Test
    void removingOneOfTwoLeavesOneActivityAndNoJourney() {
        Trip after = remove(appliedDay(2, TransportationMode.WALKING), "e0",
                new RecordingDistances(12));

        assertEquals(List.of("e1"), idsOf(after, EventType.ACTIVITY));
        assertTrue(idsOf(after, EventType.TRAVEL).isEmpty(),
                "one activity has nothing to travel between" + describe(after));
    }

    // 5
    @Test
    void removingTwoConsecutiveActivitiesRecomputesTheNewAdjacency() {
        RecordingDistances distances = new RecordingDistances(12);
        Trip after = remove(remove(appliedDay(4, TransportationMode.WALKING), "e1", distances),
                "e2", distances);

        assertEquals(List.of("e0", "e3"), idsOf(after, EventType.ACTIVITY));
        assertEquals(List.of("travel-e3"), idsOf(after, EventType.TRAVEL), describe(after));
        assertTravelIsSoundlyConnected(after);
    }

    // 6
    @Test
    void removingEveryActivityLeavesNothingAtAll() {
        Trip current = appliedDay(3, TransportationMode.WALKING);
        RecordingDistances distances = new RecordingDistances(12);
        for (String id : List.of("e0", "e1", "e2")) {
            current = remove(current, id, distances);
        }

        assertTrue(idsOf(current, EventType.ACTIVITY).isEmpty(), describe(current));
        assertTrue(idsOf(current, EventType.TRAVEL).isEmpty(),
                "an empty day cannot contain journeys" + describe(current));
    }

    // 7
    @Test
    void everyRemainingActivityAppearsExactlyOnce() {
        Trip after = remove(appliedDay(4, TransportationMode.WALKING), "e1",
                new RecordingDistances(12));

        List<String> ids = idsOf(after, EventType.ACTIVITY);
        assertEquals(new HashSet<>(ids).size(), ids.size(), "no duplicates" + describe(after));
        assertEquals(3, ids.size());
    }

    // 11
    @Test
    void theReplacementJourneyUsesTheTripsOwnTransportMode() {
        RecordingDistances distances = new RecordingDistances(12);

        remove(appliedDay(4, TransportationMode.TRANSIT), "e2", distances);

        assertFalse(distances.modesAsked.isEmpty(), "a replacement leg should be estimated");
        for (TransportationMode asked : distances.modesAsked) {
            assertEquals(TransportationMode.TRANSIT, asked,
                    "the mode is recoverable from the trip and must not be guessed");
        }
    }

    // 14
    @Test
    void aFailingProviderLeavesASoundScheduleRatherThanAHalfEditedOne() {
        RecordingDistances broken = new RecordingDistances(12);
        broken.broken = true;

        Trip after = remove(appliedDay(4, TransportationMode.WALKING), "e2", broken);

        assertEquals(List.of("e0", "e1", "e3"), idsOf(after, EventType.ACTIVITY),
                "the removal itself still succeeds" + describe(after));
        assertTrue(idsOf(after, EventType.TRAVEL).contains("travel-e1"),
                "and the journeys that were never in doubt survive" + describe(after));
        assertFalse(idsOf(after, EventType.TRAVEL).contains("travel-e3"),
                "the one that could not be estimated is absent rather than invented"
                        + describe(after));
        assertTravelIsSoundlyConnected(after);
    }

    /** A replacement journey lands exactly when its activity starts, as the scheduler does. */
    @Test
    void aReplacementJourneyArrivesAsItsActivityBegins() {
        Trip after = remove(appliedDay(4, TransportationMode.WALKING), "e2",
                new RecordingDistances(12));

        ScheduledEvent leg = null;
        ScheduledEvent destination = null;
        for (ScheduledEvent event : after.getScheduledEvents()) {
            if (event.getId().equals("travel-e3")) {
                leg = event;
            }
            if (event.getId().equals("e3")) {
                destination = event;
            }
        }
        assertNotNull(leg, describe(after));
        assertNotNull(destination);
        assertEquals(destination.getStartTime(), leg.getEndTime(),
                "a journey that lands early leaves an unexplained wait" + describe(after));
        assertEquals(12, (leg.getEndTime().toSecondOfDay()
                - leg.getStartTime().toSecondOfDay()) / 60, "and lasts what it was estimated at");
    }

    /** A journey is never drawn where there is not room for it. */
    @Test
    void noJourneyIsDrawnWhenTheGapIsTooSmallToHoldIt() {
        Trip after = remove(appliedDay(4, TransportationMode.WALKING), "e2",
                new RecordingDistances(600));

        assertTrue(idsOf(after, EventType.TRAVEL).stream().noneMatch("travel-e3"::equals),
                "ten hours of travel does not fit in the gap, so it is not claimed"
                        + describe(after));
        assertTravelIsSoundlyConnected(after);
    }
}
