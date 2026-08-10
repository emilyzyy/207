package interface_adapter.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import use_case.usecases.OptimizeItineraryInputBoundary;
import use_case.usecases.OptimizeItineraryInputData;

final class OptimizeItineraryControllerTest {

    @Test
    void executePassesTheActiveTripIdToTheInteractor() {
        final RecordingInputBoundary interactor = new RecordingInputBoundary();
        final OptimizeItineraryController controller =
                new OptimizeItineraryController(interactor, "trip-1");

        controller.execute();

        assertNotNull(interactor.input);
        assertEquals("trip-1", interactor.input.getTripId());
    }

    private static final class RecordingInputBoundary
            implements OptimizeItineraryInputBoundary {
        private OptimizeItineraryInputData input;

        @Override
        public void execute(OptimizeItineraryInputData inputData) {
            input = inputData;
        }
    }
}
