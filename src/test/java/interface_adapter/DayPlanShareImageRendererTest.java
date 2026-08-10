package interface_adapter;

import entity.entities.Trip;
import entity.entities.TripDay;
import entity.valueobjects.TransportationMode;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DayPlanShareImageRendererTest {

    @Test
    void rendersOneImagePerDayForMultiDayTrip() {
        Trip trip = new Trip(
                "trip-md",
                "Toronto",
                TransportationMode.WALKING,
                Arrays.asList(
                        new TripDay(LocalDate.of(2026, 8, 10), LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new TripDay(LocalDate.of(2026, 8, 11), LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new TripDay(LocalDate.of(2026, 8, 12), LocalTime.of(9, 0), LocalTime.of(18, 0))));

        List<BufferedImage> images = DayPlanShareImageRenderer.renderTrip(trip);

        assertEquals(3, images.size());
        for (BufferedImage image : images) {
            assertTrue(image.getWidth() > 0);
            assertTrue(image.getHeight() > 0);
        }
    }

    @Test
    void rendersSingleImageForOneDayTrip() {
        Trip trip = new Trip(
                "trip-1",
                "Montreal",
                LocalDate.of(2026, 8, 10),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.TRANSIT);

        List<BufferedImage> images = DayPlanShareImageRenderer.renderTrip(trip);

        assertEquals(1, images.size());
        assertFalse(images.get(0).getWidth() <= 0);
    }
}
