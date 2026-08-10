package use_case.usecases;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.EventType;
import use_case.ports.TripRepository;

/**
 * First-pass valid schedule compaction for activities already in an itinerary.
 *
 * <p>This milestone implementation deliberately ignores bookmarks and removes
 * existing travel events. It preserves the current activity order and all
 * non-schedule Trip state.</p>
 */
public final class OptimizeItineraryInteractor implements OptimizeItineraryInputBoundary {
    private final TripRepository trips;
    private final OptimizeItineraryOutputBoundary output;

    public OptimizeItineraryInteractor(
            TripRepository trips, OptimizeItineraryOutputBoundary output) {
        if (trips == null || output == null) {
            throw new IllegalArgumentException("Optimize itinerary dependencies are required");
        }
        this.trips = trips;
        this.output = output;
    }

    @Override
    public void execute(OptimizeItineraryInputData inputData) {
        try {
            final String tripId = requireTripId(inputData);
            final Trip trip = trips.findById(tripId)
                    .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
            final List<ScheduledEvent> activities = scheduledActivities(trip);
            if (activities.isEmpty()) {
                throw new IllegalArgumentException(
                        "Add activities to the Day Plan before optimizing");
            }

            final List<ScheduledEvent> compacted = compact(trip, activities);
            final Trip updated = trip.copyWithSchedule(compacted);
            final Trip saved = trips.save(updated);
            output.presentSuccess(new OptimizeItineraryOutputData(
                    saved, "Current itinerary compacted successfully"));
        }
        catch (IllegalArgumentException | IllegalStateException exception) {
            output.presentFailure(exception.getMessage());
        }
    }

    private String requireTripId(OptimizeItineraryInputData inputData) {
        if (inputData == null || inputData.getTripId() == null
                || inputData.getTripId().trim().isEmpty()) {
            throw new IllegalArgumentException("Trip id is required");
        }
        return inputData.getTripId().trim();
    }

    private List<ScheduledEvent> scheduledActivities(Trip trip) {
        final List<ScheduledEvent> activities = new ArrayList<ScheduledEvent>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getEventType() == EventType.ACTIVITY) {
                activities.add(event);
            }
        }
        return activities;
    }

    private List<ScheduledEvent> compact(
            Trip trip, List<ScheduledEvent> scheduledActivities) {
        final List<ScheduledEvent> compacted = new ArrayList<ScheduledEvent>();
        LocalTime cursor = trip.getStartTime();
        for (ScheduledEvent existing : scheduledActivities) {
            final Activity activity = existing.getActivity();
            final long duration = Duration.between(
                    existing.getStartTime(), existing.getEndTime()).toMinutes();
            if (activity == null || duration <= 0 || duration > Integer.MAX_VALUE) {
                throw new IllegalStateException("Scheduled activity duration is invalid");
            }

            final LocalTime start = cursor.isBefore(activity.getOpeningTime())
                    ? activity.getOpeningTime() : cursor;
            final LocalTime end = plusWithoutDayRollover(start, (int) duration);
            if (end == null || end.isAfter(activity.getClosingTime())
                    || end.isAfter(trip.getEndTime())) {
                throw new IllegalStateException(
                        "Current itinerary cannot be compacted inside trip and opening hours");
            }

            compacted.add(new ScheduledEvent(
                    existing.getId(), activity, start, end,
                    EventType.ACTIVITY, existing.getNotes()));
            cursor = end;
        }
        return compacted;
    }

    private LocalTime plusWithoutDayRollover(LocalTime time, int minutes) {
        final LocalTime result = time.plusMinutes(minutes);
        return minutes > 0 && !result.isAfter(time) ? null : result;
    }
}
