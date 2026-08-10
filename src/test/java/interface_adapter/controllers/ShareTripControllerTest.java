package interface_adapter.controllers;

import entity.entities.Trip;
import entity.entities.TripDay;
import entity.valueobjects.TransportationMode;
import use_case.ports.TripRepository;
import use_case.usecases.ShareTripInputBoundary;
import use_case.usecases.ShareTripOutputBoundary;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShareTripControllerTest {

    @Test
    void sendsActiveTripShareTextToOutput() {
        RecordingOutput output = new RecordingOutput();
        ShareTripController controller = new ShareTripController(
                tripId -> "Share " + tripId,
                () -> "trip-42",
                output);

        controller.execute();

        assertEquals("Share trip-42", output.success);
        assertNull(output.failure);
        assertTrue(output.images.isEmpty());
    }

    @Test
    void rendersOnePngPerDayWhenTripRepositoryIsProvided() {
        Trip trip = new Trip(
                "trip-42",
                "Toronto",
                TransportationMode.WALKING,
                Arrays.asList(
                        new TripDay(LocalDate.of(2026, 8, 10), LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new TripDay(LocalDate.of(2026, 8, 11), LocalTime.of(9, 0), LocalTime.of(18, 0))));
        RecordingOutput output = new RecordingOutput();
        ShareTripController controller = new ShareTripController(
                tripId -> "Share " + tripId,
                () -> "trip-42",
                output,
                new FixedTripRepository(trip));

        controller.execute();

        assertEquals("Share trip-42", output.success);
        assertEquals(2, output.images.size());
    }

    @Test
    void convertsUseCaseValidationIntoFailureOutput() {
        RecordingOutput output = new RecordingOutput();
        ShareTripInputBoundary failing = tripId -> {
            throw new IllegalArgumentException("Create a trip before sharing");
        };
        ShareTripController controller = new ShareTripController(
                failing, () -> "", output);

        controller.execute();

        assertEquals("Create a trip before sharing", output.failure);
        assertNull(output.success);
    }

    private static final class RecordingOutput implements ShareTripOutputBoundary {
        private String success;
        private String failure;
        private List<BufferedImage> images = Collections.emptyList();

        @Override
        public void presentSuccess(String shareText) {
            success = shareText;
            images = Collections.emptyList();
        }

        @Override
        public void presentSuccess(String shareText, List<BufferedImage> dayImages) {
            success = shareText;
            images = dayImages == null ? Collections.emptyList() : dayImages;
        }

        @Override
        public void presentFailure(String errorMessage) {
            failure = errorMessage;
        }
    }

    private static final class FixedTripRepository implements TripRepository {
        private final Trip trip;

        private FixedTripRepository(Trip trip) {
            this.trip = trip;
        }

        @Override
        public Trip save(Trip value) {
            return value;
        }

        @Override
        public Optional<Trip> findById(String id) {
            return trip.getId().equals(id) ? Optional.of(trip) : Optional.empty();
        }

        @Override
        public List<Trip> findAll() {
            return Collections.singletonList(trip);
        }
    }
}
