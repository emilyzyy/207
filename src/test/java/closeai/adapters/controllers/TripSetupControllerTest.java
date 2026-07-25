package closeai.adapters.controllers;

import closeai.application.usecases.CreateTripInputBoundary;
import closeai.application.usecases.CreateTripInputData;
import closeai.application.usecases.EditItineraryInputBoundary;
import closeai.application.usecases.EditItineraryInputData;
import closeai.application.usecases.TripSetupOutputBoundary;
import closeai.application.usecases.TripSetupOutputData;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TripSetupControllerTest {

    @Test
    void createsWhenThereIsNoActiveTrip() {
        RecordingCreate create = new RecordingCreate();
        RecordingEdit edit = new RecordingEdit();
        RecordingOutput output = new RecordingOutput();
        TripSetupController controller = new TripSetupController(
                create, edit, () -> "", output);

        controller.execute(
                "Montreal", "2026-08-02", "09:00", "18:00", "TRANSIT");

        assertNotNull(create.input);
        assertEquals("Montreal", create.input.getDestination());
        assertEquals(TransportationMode.TRANSIT,
                create.input.getTransportationMode());
        assertTrue(output.success.isCreated());
        assertNull(edit.input);
    }

    @Test
    void editsWhenAnActiveTripExists() {
        AtomicReference<String> activeId = new AtomicReference<String>("trip-1");
        RecordingCreate create = new RecordingCreate();
        RecordingEdit edit = new RecordingEdit();
        RecordingOutput output = new RecordingOutput();
        TripSetupController controller = new TripSetupController(
                create, edit, activeId::get, output);

        controller.execute(
                "Ottawa", "2026-08-03", "10:00", "19:00", "WALKING");

        assertNotNull(edit.input);
        assertEquals("trip-1", edit.input.getItineraryId());
        assertEquals("Ottawa", edit.input.getDestination());
        assertFalse(output.success.isCreated());
        assertNull(create.input);
    }

    @Test
    void reportsFriendlyParsingErrorsWithoutCallingAUseCase() {
        RecordingCreate create = new RecordingCreate();
        RecordingEdit edit = new RecordingEdit();
        RecordingOutput output = new RecordingOutput();
        TripSetupController controller = new TripSetupController(
                create, edit, () -> "", output);

        controller.execute(
                "Toronto", "08/02/2026", "morning", "18:00", "WALKING");

        assertEquals("Date must use YYYY-MM-DD", output.failure);
        assertNull(create.input);
        assertNull(edit.input);
    }

    private static Trip trip(String id, String destination) {
        return new Trip(
                id, destination, LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                TransportationMode.WALKING);
    }

    private static final class RecordingCreate implements CreateTripInputBoundary {
        private CreateTripInputData input;

        @Override
        public Trip execute(CreateTripInputData inputData) {
            input = inputData;
            return trip("created-trip", inputData.getDestination());
        }
    }

    private static final class RecordingEdit implements EditItineraryInputBoundary {
        private EditItineraryInputData input;

        @Override
        public Trip execute(EditItineraryInputData inputData) {
            input = inputData;
            return trip(inputData.getItineraryId(), inputData.getDestination());
        }
    }

    private static final class RecordingOutput implements TripSetupOutputBoundary {
        private TripSetupOutputData success;
        private String failure;

        @Override
        public void presentSuccess(TripSetupOutputData outputData) {
            success = outputData;
        }

        @Override
        public void presentFailure(String errorMessage) {
            failure = errorMessage;
        }
    }
}
