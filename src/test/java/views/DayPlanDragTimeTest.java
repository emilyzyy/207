package views;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

final class DayPlanDragTimeTest {
    @Test
    void dropRoundsToTheNearestQuarterHour() {
        assertEquals(LocalTime.of(10, 15), DayPlanPanel.draggedStartFor(
                LocalTime.of(9, 0), LocalTime.of(18, 0), 92, 72, 60));
    }

    @Test
    void draggingAboveTheTimelineClampsToTheDayStart() {
        assertEquals(LocalTime.of(9, 0), DayPlanPanel.draggedStartFor(
                LocalTime.of(9, 0), LocalTime.of(18, 0), -500, 72, 60));
    }

    @Test
    void draggingBelowTheTimelineClampsSoTheEventEndsAtTheDayEnd() {
        assertEquals(LocalTime.of(16, 30), DayPlanPanel.draggedStartFor(
                LocalTime.of(9, 0), LocalTime.of(18, 0), 5_000, 72, 90));
    }
}
