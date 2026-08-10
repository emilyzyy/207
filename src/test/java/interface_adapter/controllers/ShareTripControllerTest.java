package interface_adapter.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import database.persistence.InMemoryItineraryDataAccessObject;
import entity.entities.Trip;
import entity.valueobjects.TransportationMode;
import use_case.usecases.GetTripSummaryUseCase;
import use_case.usecases.ShareTripOutputBoundary;
import use_case.usecases.ShareTripOutputData;
import use_case.usecases.ShareTripUseCase;

final class ShareTripControllerTest {

    @Test
    void sendsActiveTripIdToUseCase() {
        final InMemoryItineraryDataAccessObject trips = new InMemoryItineraryDataAccessObject();
        trips.save(new Trip(
                "trip-42", "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING));
        final RecordingOutput output = new RecordingOutput();
        final ShareTripController controller = new ShareTripController(
                new ShareTripUseCase(new GetTripSummaryUseCase(trips), trips, output),
                () -> "trip-42");

        controller.execute();

        assertNotNull(output.success);
        assertEquals("trip-42", output.success.getTrip().getId());
        assertNull(output.failure);
    }

    @Test
    void convertsUseCaseValidationIntoFailureOutput() {
        final InMemoryItineraryDataAccessObject trips = new InMemoryItineraryDataAccessObject();
        final RecordingOutput output = new RecordingOutput();
        final ShareTripController controller = new ShareTripController(
                new ShareTripUseCase(new GetTripSummaryUseCase(trips), trips, output),
                () -> "");

        controller.execute();

        assertEquals("Create a trip before sharing", output.failure);
        assertNull(output.success);
    }

    private static final class RecordingOutput implements ShareTripOutputBoundary {
        private ShareTripOutputData success;
        private String failure;

        @Override
        public void presentSuccess(ShareTripOutputData outputData) {
            success = outputData;
        }

        @Override
        public void presentFailure(String errorMessage) {
            failure = errorMessage;
        }
    }
}
