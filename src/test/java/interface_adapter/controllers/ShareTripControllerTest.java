package interface_adapter.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import use_case.usecases.ShareTripInputBoundary;
import use_case.usecases.ShareTripOutputBoundary;

final class ShareTripControllerTest {

    @Test
    void sendsActiveTripShareTextToOutput() {
        final RecordingOutput output = new RecordingOutput();
        final ShareTripController controller = new ShareTripController(
                tripId -> "Share " + tripId,
                () -> "trip-42",
                output);

        controller.execute();

        assertEquals("Share trip-42", output.success);
        assertNull(output.failure);
    }

    @Test
    void convertsUseCaseValidationIntoFailureOutput() {
        final RecordingOutput output = new RecordingOutput();
        final ShareTripInputBoundary failing = tripId -> {
            throw new IllegalArgumentException("Create a trip before sharing");
        };
        final ShareTripController controller = new ShareTripController(
                failing, () -> "", output);

        controller.execute();

        assertEquals("Create a trip before sharing", output.failure);
        assertNull(output.success);
    }

    private static final class RecordingOutput implements ShareTripOutputBoundary {
        private String success;
        private String failure;

        @Override
        public void presentSuccess(String shareText) {
            success = shareText;
        }

        @Override
        public void presentFailure(String errorMessage) {
            failure = errorMessage;
        }
    }
}
