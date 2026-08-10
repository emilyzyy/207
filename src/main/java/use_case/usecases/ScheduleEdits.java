package use_case.usecases;

import java.util.ArrayList;
import java.util.List;

import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;

/**
 * Rules shared by the use cases that let a traveller edit the day by hand.
 *
 * <p>The only rule here so far is about travel, and it exists because travel blocks are
 * <em>derived</em> data. Autoschedule works out journeys for one particular arrangement of
 * activities and stores them alongside those activities, so "Travel to the CN Tower" is only
 * meaningful while the CN Tower is still in the plan, still in that position, and still at
 * that time. Change any of those by hand and the block is describing a journey nobody is
 * making.</p>
 *
 * <p>Left alone, those blocks survived every manual edit: removing an activity kept the
 * travel that led to it, and a Day Plan emptied of activities still showed a column of
 * journeys to places that were no longer in it. Dropping them on any hand edit is the
 * honest option — the day genuinely has no computed journeys again until Autoschedule is
 * run, and showing none is truthful where showing stale ones is not.</p>
 */
final class ScheduleEdits {

    private ScheduleEdits() {

    }
    /**
     * The same events with every generated travel block removed.
     *
     * <p>Activities are returned untouched and in their existing order; only their derived
     * connective tissue goes.</p>
      * @param events the e ve nt s value
      * @return the result of the operation
     */

    static List<ScheduledEvent> withoutDerivedTravel(List<ScheduledEvent> events) {
        final List<ScheduledEvent> kept = new ArrayList<>();
        if (events == null) {
            return kept;
        }
        for (ScheduledEvent event : events) {
            if (event.getEventType() != EventType.TRAVEL) {
                kept.add(event);
            }
        }
        return kept;
    }
}
