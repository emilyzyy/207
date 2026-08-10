package interface_adapter.controllers;

import java.util.function.Supplier;

import entity.entities.Trip;
import interface_adapter.presenters.ManualPlanPresenter;
import use_case.ports.TripRepository;

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

    /**
     * Makes the given day the active one and re-renders the Day Plan around it.
     * @param dayIndex the d ay in de x value
     */
    public void switchTo(int dayIndex) {
        try {
            final Trip trip = trips.findById(requireTripId())
                    .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
            if (dayIndex < 0 || dayIndex >= trip.getDayCount()) {
                throw new IllegalArgumentException("Day out of range");
            }
            trip.setActiveDayIndex(dayIndex);
            final Trip saved = trips.save(trip);
            presenter.presentSuccess(saved, "Showing " + saved.getDay(dayIndex).getDate()
                    + ".");
        }
        catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }

    private String requireTripId() {
        final String current = tripId.get();
        if (current == null || current.trim().isEmpty()) {
            throw new IllegalArgumentException("Create a trip before switching days");
        }
        return current.trim();
    }
}
