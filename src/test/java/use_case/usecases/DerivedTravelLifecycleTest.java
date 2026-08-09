package use_case.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.AppBuilder;
import app.AppContainer;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.EventType;
import entity.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Travel blocks are derived from one particular arrangement of activities, so they must not
 * outlive it.
 *
 * <p>Before these tests the three hand-edit use cases kept them: removing an activity left
 * the journey that led to it, and a Day Plan emptied of activities still displayed a column
 * of journeys to places no longer in the plan. That is the corruption a user reported, and
 * every test here fails against the old behaviour.</p>
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
        List<ScheduledEvent> acts = new ArrayList<>();
        for (ScheduledEvent event : source.getScheduledEvents()) {
            if (event.getEventType() == EventType.ACTIVITY) {
                acts.add(event);
            }
        }
        List<ScheduledEvent> combined = new ArrayList<>();
        for (int i = 0; i < acts.size(); i++) {
            if (i > 0) {
                ScheduledEvent previous = acts.get(i - 1);
                combined.add(new ScheduledEvent("travel-" + i, null, previous.getEndTime(),
                        previous.getEndTime().plusMinutes(10), EventType.TRAVEL,
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
        List<Activity> pool = app.activities.findAll();
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

    @Test
    void removingTheFirstActivityLeavesNoOrphanedTravel() {
        Trip after = app.removeEvent.execute("t", activities.get(0).getId());

        assertEquals(2, countOf(after, EventType.ACTIVITY));
        assertEquals(0, countOf(after, EventType.TRAVEL));
    }

    @Test
    void removingAMiddleActivityLeavesNoOrphanedTravel() {
        Trip after = app.removeEvent.execute("t", activities.get(1).getId());

        assertEquals(2, countOf(after, EventType.ACTIVITY));
        assertEquals(0, countOf(after, EventType.TRAVEL),
                "the journey to a removed activity describes a trip nobody is making");
    }

    @Test
    void removingTheFinalActivityLeavesNoOrphanedTravel() {
        Trip after = app.removeEvent.execute("t", activities.get(2).getId());

        assertEquals(2, countOf(after, EventType.ACTIVITY));
        assertEquals(0, countOf(after, EventType.TRAVEL));
    }

    @Test
    void removingTwoConsecutiveActivitiesLeavesNoOrphanedTravel() {
        app.removeEvent.execute("t", activities.get(0).getId());
        Trip after = app.removeEvent.execute("t", activities.get(1).getId());

        assertEquals(1, countOf(after, EventType.ACTIVITY));
        assertEquals(0, countOf(after, EventType.TRAVEL));
    }

    /** The exact state a user reported: a plan with no activities still showing journeys. */
    @Test
    void anEmptiedDayPlanContainsNoTravelAtAll() {
        for (ScheduledEvent activity : activities) {
            app.removeEvent.execute("t", activity.getId());
        }
        Trip after = app.trips.findById("t").orElseThrow();

        assertEquals(0, countOf(after, EventType.ACTIVITY));
        assertEquals(0, countOf(after, EventType.TRAVEL),
                "an empty Day Plan cannot contain journeys");
        assertTrue(after.getScheduledEvents().isEmpty());
    }

    @Test
    void addingAnActivityByHandDropsTheStaleJourneys() {
        Trip after = app.addActivityToPlan.execute("t",
                app.activities.findAll().get(3).getId(),
                LocalTime.of(18, 0), LocalTime.of(19, 0));

        assertEquals(4, countOf(after, EventType.ACTIVITY), "the new activity is added");
        assertEquals(0, countOf(after, EventType.TRAVEL),
                "the route changed, so the computed journeys no longer describe this day");
    }

    @Test
    void retimingAnActivityByHandDropsTheStaleJourneys() {
        Trip after = app.editEvent.execute("t", activities.get(1).getId(),
                LocalTime.of(16, 0), LocalTime.of(17, 0), "moved by hand");

        assertEquals(3, countOf(after, EventType.ACTIVITY), "no activity is lost");
        assertEquals(0, countOf(after, EventType.TRAVEL),
                "journeys either side of a retimed activity are stale");
        assertEquals(LocalTime.of(16, 0),
                after.findEvent(activities.get(1).getId()).getStartTime());
    }

    @Test
    void everyRemainingActivityKeepsItsIdentityAndDuration() {
        Trip after = app.removeEvent.execute("t", activities.get(1).getId());

        for (ScheduledEvent original : List.of(activities.get(0), activities.get(2))) {
            ScheduledEvent kept = after.findEvent(original.getId());
            assertEquals(original.getStartTime(), kept.getStartTime());
            assertEquals(original.getEndTime(), kept.getEndTime());
            assertEquals(original.getActivity().getId(), kept.getActivity().getId());
        }
    }
}
