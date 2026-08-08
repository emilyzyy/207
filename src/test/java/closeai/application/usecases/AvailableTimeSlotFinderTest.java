package closeai.application.usecases;

import closeai.domain.entities.ScheduledEvent;
import closeai.domain.valueobjects.EventType;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class AvailableTimeSlotFinderTest {
    private final AvailableTimeSlotFinder finder = new AvailableTimeSlotFinder();

    @Test
    void choosesTheEarliestOneHourGapEvenWhenAShorterGapAppearsFirst() {
        AvailableTimeSlotFinder.Slot slot = finder.find(
                LocalTime.of(9, 0), LocalTime.of(14, 0), Arrays.asList(
                        event("a", 9, 30, 10, 0), event("b", 11, 0, 13, 0)));
        assertEquals(LocalTime.of(10, 0), slot.getStart());
        assertEquals(LocalTime.of(11, 0), slot.getEnd());
    }

    @Test
    void fallsBackThroughFortyFiveThirtyAndFifteenMinutes() {
        AvailableTimeSlotFinder.Slot slot = finder.find(
                LocalTime.of(9, 0), LocalTime.of(10, 45),
                Collections.singletonList(event("a", 9, 45, 10, 15)));
        assertEquals(LocalTime.of(9, 0), slot.getStart());
        assertEquals(LocalTime.of(9, 45), slot.getEnd());
    }

    @Test
    void returnsNullWhenNoQuarterHourCanFit() {
        AvailableTimeSlotFinder.Slot slot = finder.find(
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                Collections.singletonList(event("a", 9, 0, 10, 0)));
        assertNull(slot);
    }

    private ScheduledEvent event(String id, int sh, int sm, int eh, int em) {
        return new ScheduledEvent(id, null, LocalTime.of(sh, sm), LocalTime.of(eh, em),
                EventType.TRAVEL, "Busy");
    }
}
