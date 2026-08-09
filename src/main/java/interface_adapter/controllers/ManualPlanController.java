package interface_adapter.controllers;

import interface_adapter.presenters.ManualPlanPresenter;
import interface_adapter.viewmodels.TimeDisplay;
import use_case.usecases.AddActivityToPlanUseCase;
import use_case.usecases.EditScheduledEventUseCase;
import use_case.usecases.RemoveScheduledEventUseCase;
import entity.entities.Trip;
import java.time.LocalTime;
import java.util.function.Supplier;

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

    public void add(String activityId, String preferredStart) {
        try {
            Trip trip = add.execute(requireTripId(), activityId, optionalTime(preferredStart));
            presenter.presentSuccess(trip, "Activity added to the Day Plan");
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    public void add(String activityId, LocalTime start, LocalTime end) {
        try {
            Trip trip = add.execute(requireTripId(), activityId, start, end);
            presenter.presentSuccess(trip, "Activity added to the Day Plan");
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    public void edit(String eventId, String start, String end, String notes) {
        try {
            Trip trip = edit.execute(
                    requireTripId(), eventId, requiredTime(start, "Start time"),
                    requiredTime(end, "End time"), notes);
            presenter.presentSuccess(trip, "Scheduled event updated");
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    public void remove(String eventId) {
        try {
            presenter.presentSuccess(
                    remove.execute(requireTripId(), eventId), "Scheduled event removed");
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    private String requireTripId() {
        String current = tripId.get();
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
     */
    private static LocalTime requiredTime(String value, String label) {
        LocalTime parsed = TimeDisplay.parse(value);
        if (parsed == null) {
            throw new IllegalArgumentException(label + " must look like 9:00 AM");
        }
        return parsed;
    }
}
