package interface_adapter.controllers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import entity.valueobjects.TransportationMode;
import use_case.usecases.CreateTripInputBoundary;
import use_case.usecases.CreateTripInputData;

/** Controller that converts UI parameters into create-trip input data. */
public final class CreateTripController {
    private final CreateTripInputBoundary createTrip;

    /**
     * Creates a create-trip controller.
     *
     * @param createTrip the create-trip input boundary
     * @throws IllegalArgumentException if the boundary is missing
     */
    public CreateTripController(CreateTripInputBoundary createTrip) {
        if (createTrip == null) {
            throw new IllegalArgumentException("Create trip use case is required");
        }
        this.createTrip = createTrip;
    }

    /**
     * Creates a trip from UI parameters.
     *
     * @param destination destination name
     * @param date start date
     * @param start start time
     * @param end end time
     * @param mode transportation mode
     * @param dayCount number of trip days
     * @param companionIds friend ids to share with, or null for none
     */
    public void create(
            String destination,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            TransportationMode mode,
            int dayCount,
            List<String> companionIds) {
        createTrip.execute(new CreateTripInputData(
                destination, date, start, end, mode, dayCount, companionIds));
    }
}
