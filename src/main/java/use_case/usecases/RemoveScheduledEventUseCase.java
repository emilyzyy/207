package use_case.usecases;

import java.util.List;

import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import use_case.ports.DistanceService;
import use_case.ports.TripRepository;

/**
 * Takes one activity out of the day and repairs the journeys around it.
 *
 * <p>Removal used to drop every generated travel block, so taking one activity out of an
 * applied four-activity day also erased the journeys between the three that stayed. Travel is
 * derived from the sequence, not from any one activity, so only the legs into and out of the
 * removed activity were ever invalid. See {@link DerivedTravel}.</p>
 */
public final class RemoveScheduledEventUseCase {
    private final TripRepository trips;
    private final DistanceService distances;

    /**
      * @param trips the t ri ps value
     *                  newly adjacent pair is simply left without a drawn leg rather than
     *                  being given a guessed one
     * @param distances used to estimate the one journey the removal creates; when absent the
     */
    public RemoveScheduledEventUseCase(TripRepository trips, DistanceService distances) {
        this.trips = trips;
        this.distances = distances;
    }

    public RemoveScheduledEventUseCase(TripRepository trips) {
        this(trips, null);
    }

    /**
     * Performs the e xe cu te operation.
     * @param eventId the e ve nt id value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public Trip execute(String tripId, String eventId) {
        final Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        if (trip.findEvent(eventId) == null) {
            throw new IllegalArgumentException("Event not found");
        }
        // The whole day is rebuilt in memory first, so a failure to estimate the replacement
        // journey cannot leave a half-edited schedule behind: either this list saves or
        // nothing changes.
        final List<ScheduledEvent> updated = DerivedTravel.afterRemoving(trip.getScheduledEvents(),
                eventId, trip.getTransportationMode(), trip.getDate(), distances);
        return trips.save(trip.copyWithSchedule(updated));
    }
}
