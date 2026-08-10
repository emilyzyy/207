package interface_adapter.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import entity.valueobjects.TransportationMode;
import use_case.usecases.CreateTripInputBoundary;
import use_case.usecases.CreateTripInputData;

final class CreateTripControllerTest {

    @Test
    void convertsUiParametersIntoInputData() {
        final RecordingCreateTripBoundary boundary = new RecordingCreateTripBoundary();
        final CreateTripController controller = new CreateTripController(boundary);

        controller.create("Toronto",
                LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.WALKING,
                3,
                List.of("friend-1", "friend-2"));

        final CreateTripInputData captured = boundary.captured;
        assertEquals("Toronto", captured.getDestination());
        assertEquals(LocalDate.of(2026, 8, 2), captured.getDate());
        assertEquals(LocalTime.of(9, 0), captured.getStartTime());
        assertEquals(LocalTime.of(18, 0), captured.getEndTime());
        assertEquals(TransportationMode.WALKING, captured.getTransportationMode());
        assertEquals(3, captured.getDayCount());
        assertEquals(List.of("friend-1", "friend-2"), captured.getCompanionIds());
    }

    @Test
    void rejectsNullBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateTripController(null));
    }

    private static final class RecordingCreateTripBoundary implements CreateTripInputBoundary {
        private CreateTripInputData captured;

        @Override
        public void execute(CreateTripInputData inputData) {
            captured = inputData;
        }
    }
}
