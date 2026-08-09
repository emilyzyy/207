package interface_adapter.controllers;

import use_case.usecases.OptimizeItineraryInputBoundary;
import use_case.usecases.OptimizeItineraryInputData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class OptimizeItineraryControllerTest {

    @Test
    void executePassesTheActiveTripIdToTheInteractor() {
        RecordingInputBoundary interactor = new RecordingInputBoundary();
        OptimizeItineraryController controller =
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
