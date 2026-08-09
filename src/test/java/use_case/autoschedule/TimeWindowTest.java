package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeWindowTest {

    @Test
    void rejectsEndAtOrBeforeStart() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimeWindow(LocalTime.of(10, 0), LocalTime.of(10, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeWindow(LocalTime.of(11, 0), LocalTime.of(10, 0)));
    }

    @Test
    void containsIsHalfOpen() {
        TimeWindow window = new TimeWindow(LocalTime.of(9, 0), LocalTime.of(10, 0));
        assertTrue(window.contains(LocalTime.of(9, 0)));
        assertTrue(window.contains(LocalTime.of(9, 59)));
        assertFalse(window.contains(LocalTime.of(10, 0)));
    }

    @Test
    void adjacentWindowsDoNotOverlap() {
        TimeWindow morning = new TimeWindow(LocalTime.of(9, 0), LocalTime.of(10, 0));
        TimeWindow next = new TimeWindow(LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertFalse(morning.overlaps(next));
        assertFalse(next.overlaps(morning));
    }

    @Test
    void detectsGenuineOverlap() {
        TimeWindow first = new TimeWindow(LocalTime.of(9, 0), LocalTime.of(10, 30));
        TimeWindow second = new TimeWindow(LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertTrue(first.overlaps(second));
        assertTrue(second.overlaps(first));
    }

    @Test
    void enclosesRequiresBothBounds() {
        TimeWindow day = new TimeWindow(LocalTime.of(9, 0), LocalTime.of(21, 0));
        assertTrue(day.encloses(new TimeWindow(LocalTime.of(9, 0), LocalTime.of(21, 0))));
        assertFalse(day.encloses(new TimeWindow(LocalTime.of(8, 59), LocalTime.of(12, 0))));
        assertFalse(day.encloses(new TimeWindow(LocalTime.of(12, 0), LocalTime.of(21, 1))));
    }

    @Test
    void reportsDurationInMinutes() {
        assertEquals(90, new TimeWindow(LocalTime.of(9, 0), LocalTime.of(10, 30)).durationMinutes());
    }
}
