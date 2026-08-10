package interface_adapter.controllers;

import java.time.LocalTime;
import java.util.function.Supplier;

import entity.entities.Trip;
import interface_adapter.presenters.ManualPlanPresenter;
import interface_adapter.viewmodels.TimeDisplay;
import use_case.usecases.AddActivityToPlanUseCase;
import use_case.usecases.EditScheduledEventUseCase;
import use_case.usecases.RemoveScheduledEventUseCase;

/** Parses Swing input and invokes the manual Day Plan application use cases. */
public final class ManualPlanController {
    private final AddActivityToPlanUseCase add;
    private final EditScheduledEventUseCase edit;
    private final RemoveScheduledEventUseCase remove;
    private final Supplier<String> tripId;
    private final ManualPlanPresenter presenter;

    public ManualPlanController(AddActivityToPlanUseCase add,
                                EditScheduledEventUseCase edit,
                                RemoveScheduledEventUseCase remove,
                                Supplier<String> tripId,
                                ManualPlanPresenter presenter) {
        if (add == null || edit == null || remove == null
                || tripId == null || presenter == null) {
            throw new IllegalArgumentException("Manual plan dependencies are required");
        }
        this.add = add;
        this.edit = edit;
        this.remove = remove;
        this.tripId = tripId;
        this.presenter = presenter;
    }

    /**
     * Performs the a dd operation.
     * @param preferredStart the p re fe rr ed st ar t value
     * @param activityId the a ct iv it yi d value
     */
    public void add(String activityId, String preferredStart) {
        try {
            final Trip trip = add.execute(requireTripId(), activityId, optionalTime(preferredStart));
            presenter.presentSuccess(trip, "Activity added to the Day Plan");
        }
        catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    /**
     * Performs the a dd operation.
     * @param end the e nd value
     * @param start the s ta rt value
     * @param activityId the a ct iv it yi d value
     */
    public void add(String activityId, LocalTime start, LocalTime end) {
        try {
            final Trip trip = add.execute(requireTripId(), activityId, start, end);
            presenter.presentSuccess(trip, "Activity added to the Day Plan");
        }
        catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    /**
     * Performs the e di t operation.
     * @param end the e nd value
     * @param notes the n ot es value
     * @param eventId the e ve nt id value
     * @param start the s ta rt value
     */
    public void edit(String eventId, String start, String end, String notes) {
        try {
            final Trip trip = edit.execute(
                    requireTripId(), eventId, requiredTime(start, "Start time"),
                    requiredTime(end, "End time"), notes);
            presenter.presentSuccess(trip, "Scheduled event updated");
        }
        catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    /**
     * Performs the r em ov e operation.
     * @param eventId the e ve nt id value
     */
    public void remove(String eventId) {
        try {
            presenter.presentSuccess(
                    remove.execute(requireTripId(), eventId), "Scheduled event removed");
        }
        catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    private String requireTripId() {
        final String current = tripId.get();
        if (current == null || current.trim().isEmpty()) {
            throw new IllegalArgumentException("Create a trip before editing the Day Plan");
        }
        return current.trim();
    }

    private static LocalTime optionalTime(String value) {
        return value == null || value.trim().isEmpty() ? null : requiredTime(value, "Start time");
    }
    /**
     * Reads a typed time through {@link TimeDisplay}, which is what the edit dialog now
     * shows. It still accepts the 24-hour {@code HH:MM} this previously required, so no
     * existing caller or habit breaks; it simply also understands the AM/PM the field is
     * prefilled with.
      * @param value the v al ue value
      * @param label the l ab el value
      * @return the result of the operation
     */

    private static LocalTime requiredTime(String value, String label) {
        final LocalTime parsed = TimeDisplay.parse(value);
        if (parsed == null) {
            throw new IllegalArgumentException(label + " must look like 09:00 or 13:15");
        }
        return parsed;
    }
}
