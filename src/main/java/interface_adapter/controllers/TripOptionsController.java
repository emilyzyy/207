package interface_adapter.controllers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import use_case.usecases.EditItineraryInputBoundary;
import use_case.usecases.EditItineraryInputData;
import use_case.usecases.TripOptionsOutputBoundary;

/** Converts popup values into an edit-itinerary request and summarizes schedule adjustments. */
public final class TripOptionsController {
    private final EditItineraryInputBoundary edit;
    private final Supplier<Trip> activeTrip;
    private final TripOptionsOutputBoundary output;

    public TripOptionsController(EditItineraryInputBoundary edit,
                                 Supplier<Trip> activeTrip,
                                 TripOptionsOutputBoundary output) {
        if (edit == null || activeTrip == null || output == null) {
            throw new IllegalArgumentException("Trip Options dependencies are required");
        }
        this.edit = edit;
        this.activeTrip = activeTrip;
        this.output = output;
    }

    /**
     * Performs the e xe cu te operation.
     * @param end the e nd value
     * @param start the s ta rt value
     * @param date the d at e value
     */
    public void execute(LocalDate date, LocalTime start, LocalTime end) {
        try {
            final Trip before = activeTrip.get();
            if (before == null) {
                throw new IllegalArgumentException("Open a trip first");
            }
            final Map<String, String> originalTimes = eventTimes(before);
            final Trip updated = edit.execute(new EditItineraryInputData(
                    before.getId(), before.getDestination(), date, start, end,
                    before.getTransportationMode()));
            int removed = 0;
            int adjusted = 0;
            final Map<String, String> updatedTimes = eventTimes(updated);
            for (Map.Entry<String, String> event : originalTimes.entrySet()) {
                if (!updatedTimes.containsKey(event.getKey())) {
                    removed++;

                }
                else if (!event.getValue().equals(updatedTimes.get(event.getKey()))) {

                    adjusted++;
                }
            }
            output.presentSuccess(updated, message(adjusted, removed));
        }
        catch (IllegalArgumentException exception) {
            output.presentFailure(exception.getMessage());
        }
        catch (RuntimeException exception) {
            output.presentFailure("Trip options could not be saved");
        }
    }

    private static Map<String, String> eventTimes(Trip trip) {
        final Map<String, String> times = new LinkedHashMap<>();
        for (int day = 0; day < trip.getDayCount(); day++) {
            for (ScheduledEvent event : trip.getDay(day).getScheduledEvents()) {
                times.put(day + "|" + event.getId(),
                        event.getStartTime() + "|" + event.getEndTime());
            }
        }
        return times;
    }

    private static String message(int adjusted, int removed) {
        if (adjusted == 0 && removed == 0) {
            return "Trip options saved.";
        }
        final StringBuilder result = new StringBuilder("Trip options saved.");
        if (adjusted > 0) {
            result.append(' ').append(adjusted)
                    .append(adjusted == 1 ? " activity was trimmed." : " activities were trimmed.");
        }
        if (removed > 0) {
            result.append(' ').append(removed)
                    .append(removed == 1 ? " activity was removed." : " activities were removed.");
        }
        return result.toString();
    }
}
