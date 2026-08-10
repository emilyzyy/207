package interface_adapter.controllers;

import interface_adapter.presenters.ManualPlanPresenter;
import use_case.ports.TripRepository;
import entity.entities.Trip;
import java.util.function.Supplier;

/** Switches which day of a multi-day trip the Day Plan and Calendar are showing. */
public final class TripDayController {
    private final TripRepository trips;
    private final Supplier<String> tripId;
    private final ManualPlanPresenter presenter;

    public TripDayController(TripRepository trips, Supplier<String> tripId,
                             ManualPlanPresenter presenter) {
        if (trips == null || tripId == null || presenter == null) {
            throw new IllegalArgumentException("Trip day dependencies are required");
        }
        this.trips = trips;
        this.tripId = tripId;
        this.presenter = presenter;
    }

    /** Makes the given day the active one and re-renders the Day Plan around it. */
    public void switchTo(int dayIndex) {
        try {
            Trip trip = trips.findById(requireTripId())
                    .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
            if (dayIndex < 0 || dayIndex >= trip.getDayCount()) {
                throw new IllegalArgumentException("Day out of range");
            }
            trip.setActiveDayIndex(dayIndex);
            Trip saved = trips.save(trip);
            presenter.presentSuccess(saved, "Showing " + saved.getDay(dayIndex).getDate()
                    + ".");
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    private String requireTripId() {
        String current = tripId.get();
        if (current == null || current.trim().isEmpty()) {
            throw new IllegalArgumentException("Create a trip before switching days");
        }
        return current.trim();
    }
}
