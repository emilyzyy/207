package interface_adapter.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import entity.valueobjects.TransportationMode;

/**
 * What Autoschedule remembers between attempts on the same day.
 *
 * <p>Re-entering "unavailable 10:00 AM to 1:00 PM" before every run is the kind of friction
 * that makes a feature feel hostile. Remembering it is only safe while it stays visible: a
 * constraint that shapes the answer from behind a closed dialog is worse than one that was
 * never remembered, so the memory hands values back to be shown rather than applying them.</p>
 */
class AutoScheduleSettingsMemoryTest {

    private static AutoScheduleSettings settingsWith(List<AutoScheduleSettings.Window> windows) {
        return new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(18, 0),
                TransportationMode.TRANSIT, windows, false, true, true, false, true, false);
    }

    private static AutoScheduleSettings.Window window(int fromHour, int toHour) {
        return new AutoScheduleSettings.Window(LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0));
    }

    @Test
    void aDayThatHasNeverBeenScheduledRemembersNothing() {
        assertNull(new AutoScheduleSettingsMemory().remembered("trip-1", 0),
                "an empty form is the right start for a day nobody has scheduled");
    }

    @Test
    void everySettingComesBackForTheNextAttemptOnTheSameDay() {
        AutoScheduleSettingsMemory memory = new AutoScheduleSettingsMemory();
        memory.remember("trip-1", 0, settingsWith(Collections.singletonList(window(10, 13))));

        AutoScheduleSettings back = memory.remembered("trip-1", 0);

        assertNotNull(back);
        assertEquals(LocalTime.of(9, 0), back.getAvailableStart());
        assertEquals(LocalTime.of(18, 0), back.getAvailableEnd());
        assertEquals(TransportationMode.TRANSIT, back.getTransportationMode());
        assertEquals(1, back.getUnavailableWindows().size(),
                "the unavailable period is the whole reason this exists");
        assertEquals(LocalTime.of(10, 0), back.getUnavailableWindows().get(0).getStart());
        assertEquals(LocalTime.of(13, 0), back.getUnavailableWindows().get(0).getEnd());
        assertEquals(false, back.isKeepCurrentOrder());
        assertEquals(false, back.isMinimizeGaps());
        assertEquals(false, back.isPreferDaylight());
    }

    /** A multi-day trip is several separate plans, and they must not borrow each other's. */
    @Test
    void eachDayOfATripRemembersItsOwn() {
        AutoScheduleSettingsMemory memory = new AutoScheduleSettingsMemory();
        memory.remember("trip-1", 0, settingsWith(Collections.singletonList(window(10, 13))));

        assertNull(memory.remembered("trip-1", 1),
                "the second day of a trip has its own constraints");
        assertNull(memory.remembered("trip-2", 0), "and so does a different trip");
    }

    @Test
    void aLaterAttemptReplacesWhatWasRememberedRatherThanAddingToIt() {
        AutoScheduleSettingsMemory memory = new AutoScheduleSettingsMemory();
        memory.remember("trip-1", 0, settingsWith(Collections.singletonList(window(10, 13))));
        memory.remember("trip-1", 0, settingsWith(Collections.emptyList()));

        assertEquals(0, memory.remembered("trip-1", 0).getUnavailableWindows().size(),
                "removing a period in the dialog must remove it from what is remembered");
    }

    @Test
    void anExplicitResetForgetsTheDayEntirely() {
        AutoScheduleSettingsMemory memory = new AutoScheduleSettingsMemory();
        memory.remember("trip-1", 0, settingsWith(Collections.singletonList(window(10, 13))));

        memory.forget("trip-1", 0);

        assertNull(memory.remembered("trip-1", 0));
    }
}
