package use_case.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.AppBuilder;
import app.AppContainer;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.EventType;
import entity.valueobjects.TransportationMode;

/**
 * Travel blocks are derived from one particular arrangement of activities, so they must not
 * outlive it.
 *
 * <p>Two opposite faults have been reported here. First the hand-edit use cases kept every
 * journey, so a Day Plan emptied of activities still displayed a column of journeys to places
 * no longer in it. Then the correction went too far the other way: removing one activity from
 * an applied four-activity day erased the journeys between the three that remained.</p>
 *
 * <p>The contract that satisfies both: a journey survives exactly as long as the pair of
 * activities it joins stays adjacent. Removing an activity invalidates the leg into it and the
 * leg out of it, and creates one new adjacency which is estimated afresh.</p>
 */
class DerivedTravelLifecycleTest {

    private AppContainer app;
    private Trip trip;
    private List<ScheduledEvent> activities;

    private static long countOf(Trip trip, EventType type) {
        return trip.getScheduledEvents().stream()
                .filter(event -> event.getEventType() == type).count();
    }

    /** The shape the Day Plan is in immediately after an Autoschedule Apply. */
    private Trip withGeneratedTravel(Trip source) {
        final List<ScheduledEvent> acts = new ArrayList<>();
        for (ScheduledEvent event : source.getScheduledEvents()) {
            if (event.getEventType() == EventType.ACTIVITY) {
                acts.add(event);
            }
        }
        final List<ScheduledEvent> combined = new ArrayList<>();
        for (int i = 0; i < acts.size(); i++) {
            if (i > 0) {
                final ScheduledEvent previous = acts.get(i - 1);
                // The identifier Autoschedule actually materialises: travel-<destination>,
                // which is what makes "the leg into D" findable later.
                combined.add(new ScheduledEvent("travel-" + acts.get(i).getId(), null,
                        previous.getEndTime(), previous.getEndTime().plusMinutes(10),
                        EventType.TRAVEL,
                        "Travel to " + acts.get(i).getActivity().getName()));
            }
            combined.add(acts.get(i));
        }
        combined.sort(Comparator.comparing(ScheduledEvent::getStartTime));
        return app.trips.save(source.copyWithSchedule(combined));
    }

    @BeforeEach
    void buildAppliedDay() {
        app = new AppBuilder().buildOffline();
        trip = app.trips.save(new Trip("t", "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING));
        final List<Activity> pool = app.activities.findAll();
        for (int i = 0; i < 3; i++) {
            trip = app.addActivityToPlan.execute("t", pool.get(i).getId(),
                    LocalTime.of(9 + i * 3, 0), LocalTime.of(10 + i * 3, 0));
        }
        trip = withGeneratedTravel(trip);
        activities = new ArrayList<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getEventType() == EventType.ACTIVITY) {
                activities.add(event);
            }
        }
        assertEquals(3, activities.size());
        assertEquals(2, countOf(trip, EventType.TRAVEL), "precondition: the day has travel");
    }

    /** Removing the first activity takes its outgoing leg and leaves the rest alone. */
    @Test
    void removingTheFirstActivityDropsOnlyItsOwnLeg() {
        final Trip after = app.removeEvent.execute("t", activities.get(0).getId());

        assertEquals(2, countOf(after, EventType.ACTIVITY));
        assertNoOrphanedTravel(after);
        assertEquals(1, countOf(after, EventType.TRAVEL),
                "the journey between the two that remain is still true: " + describe(after));
    }

    /** The reported case: the pair either side of the gap is joined again. */
    @Test
    void removingAMiddleActivityReplacesItsTwoLegsWithOne() {
        final Trip after = app.removeEvent.execute("t", activities.get(1).getId());

        assertEquals(2, countOf(after, EventType.ACTIVITY));
        assertNoOrphanedTravel(after);
        assertEquals(1, countOf(after, EventType.TRAVEL),
                "the two remaining activities are now adjacent and need one journey: "
                        + describe(after));
    }

    @Test
    void removingTheFinalActivityDropsOnlyItsIncomingLeg() {
        final Trip after = app.removeEvent.execute("t", activities.get(2).getId());

        assertEquals(2, countOf(after, EventType.ACTIVITY));
        assertNoOrphanedTravel(after);
        assertEquals(1, countOf(after, EventType.TRAVEL), describe(after));
    }

    @Test
    void removingTwoConsecutiveActivitiesLeavesOneActivityAndNoTravel() {
        app.removeEvent.execute("t", activities.get(0).getId());
        final Trip after = app.removeEvent.execute("t", activities.get(1).getId());

        assertEquals(1, countOf(after, EventType.ACTIVITY));
        assertEquals(0, countOf(after, EventType.TRAVEL),
                "one activity has nothing to travel between: " + describe(after));
        assertNoOrphanedTravel(after);
    }

    /** No journey may name a destination that is no longer in the day. */
    private static void assertNoOrphanedTravel(Trip trip) {
        final java.util.Set<String> present = new java.util.HashSet<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getEventType() == EventType.ACTIVITY) {
                present.add(event.getId());
            }
        }
        final java.util.Set<String> destinations = new java.util.HashSet<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getEventType() != EventType.TRAVEL) {
                continue;
            }
            final String destination = event.getId().replaceFirst("^travel-", "");
            assertTrue(present.contains(destination),
                    "travel to a removed activity: " + event.getId() + describe(trip));
            assertTrue(destinations.add(destination),
                    "two journeys to the same activity: " + event.getId() + describe(trip));
        }
    }

    private static String describe(Trip trip) {
        final StringBuilder text = new StringBuilder("\n");
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            text.append("  ").append(event.getEventType()).append(' ')
                    .append(event.getStartTime()).append('-').append(event.getEndTime())
                    .append(' ').append(event.getId()).append('\n');
        }
        return text.toString();
    }

    /** The exact state a user reported: a plan with no activities still showing journeys. */
    @Test
    void anEmptiedDayPlanContainsNoTravelAtAll() {
        for (ScheduledEvent activity : activities) {
            app.removeEvent.execute("t", activity.getId());
        }
        final Trip after = app.trips.findById("t").orElseThrow();

        assertEquals(0, countOf(after, EventType.ACTIVITY));
        assertEquals(0, countOf(after, EventType.TRAVEL),
                "an empty Day Plan cannot contain journeys");
        assertTrue(after.getScheduledEvents().isEmpty());
    }

    @Test
    void addingAnActivityByHandDropsTheStaleJourneys() {
        final Trip after = app.addActivityToPlan.execute("t",
                app.activities.findAll().get(3).getId(),
                LocalTime.of(18, 0), LocalTime.of(19, 0));

        assertEquals(4, countOf(after, EventType.ACTIVITY), "the new activity is added");
        assertEquals(0, countOf(after, EventType.TRAVEL),
                "the route changed, so the computed journeys no longer describe this day");
    }

    @Test
    void retimingAnActivityByHandDropsTheStaleJourneys() {
        final Trip after = app.editEvent.execute("t", activities.get(1).getId(),
                LocalTime.of(16, 0), LocalTime.of(17, 0), "moved by hand");

        assertEquals(3, countOf(after, EventType.ACTIVITY), "no activity is lost");
        assertEquals(0, countOf(after, EventType.TRAVEL),
                "journeys either side of a retimed activity are stale");
        assertEquals(LocalTime.of(16, 0),
                after.findEvent(activities.get(1).getId()).getStartTime());
    }

    @Test
    void everyRemainingActivityKeepsItsIdentityAndDuration() {
        final Trip after = app.removeEvent.execute("t", activities.get(1).getId());

        for (ScheduledEvent original : List.of(activities.get(0), activities.get(2))) {
            final ScheduledEvent kept = after.findEvent(original.getId());
            assertEquals(original.getStartTime(), kept.getStartTime());
            assertEquals(original.getEndTime(), kept.getEndTime());
            assertEquals(original.getActivity().getId(), kept.getActivity().getId());
        }
    }
}
