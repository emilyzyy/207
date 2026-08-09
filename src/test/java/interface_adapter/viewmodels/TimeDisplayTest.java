package interface_adapter.viewmodels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * The clock the traveller reads.
 *
 * <p>Formatting is presentation only, so the interesting cases are the ones where a naive
 * {@code hour % 12} gets it wrong — midnight and noon — and the parsing side, which has to
 * accept what the field shows without rejecting what people actually type.</p>
 */
class TimeDisplayTest {

    @Test
    void formatsEveryTimeOnATwentyFourHourClock() {
        assertEquals("09:00", TimeDisplay.format(LocalTime.of(9, 0)));
        assertEquals("13:15", TimeDisplay.format(LocalTime.of(13, 15)));
        assertEquals("15:30", TimeDisplay.format(LocalTime.of(15, 30)));
        assertEquals("21:00", TimeDisplay.format(LocalTime.of(21, 0)));
    }

    @Test
    void midnightAndNoonUseUnambiguousMilitaryTime() {
        assertEquals("00:00", TimeDisplay.format(LocalTime.of(0, 0)));
        assertEquals("00:30", TimeDisplay.format(LocalTime.of(0, 30)));
        assertEquals("12:00", TimeDisplay.format(LocalTime.of(12, 0)));
        assertEquals("12:45", TimeDisplay.format(LocalTime.of(12, 45)));
    }

    @Test
    void minutesKeepTheirLeadingZero() {
        assertEquals("09:05", TimeDisplay.format(LocalTime.of(9, 5)));
    }

    @Test
    void aNullTimeIsEmptyRatherThanTheWordNull() {
        assertEquals("", TimeDisplay.format(null));
    }

    @Test
    void rangesUseAnEnDashBetweenTwoFormattedTimes() {
        assertEquals("09:00 – 10:30",
                TimeDisplay.range(LocalTime.of(9, 0), LocalTime.of(10, 30)));
    }

    // --- parsing ---------------------------------------------------------------------

    @Test
    void readsBackExactlyWhatItPrints() {
        for (int hour = 0; hour < 24; hour++) {
            LocalTime time = LocalTime.of(hour, 15);
            assertEquals(time, TimeDisplay.parse(TimeDisplay.format(time)),
                    "a field must be able to read back its own text");
        }
    }

    @Test
    void acceptsTheShapesPeopleActuallyType() {
        assertEquals(LocalTime.of(9, 0), TimeDisplay.parse("9:00 AM"));
        assertEquals(LocalTime.of(9, 0), TimeDisplay.parse("9am"));
        assertEquals(LocalTime.of(9, 0), TimeDisplay.parse("9 AM"));
        assertEquals(LocalTime.of(9, 0), TimeDisplay.parse("9"));
        assertEquals(LocalTime.of(21, 30), TimeDisplay.parse("9:30pm"));
        assertEquals(LocalTime.of(21, 30), TimeDisplay.parse("  9:30 p.m.  "));
    }

    @Test
    void stillAcceptsTheTwentyFourHourFormTheFieldsUsedToHold() {
        assertEquals(LocalTime.of(9, 0), TimeDisplay.parse("09:00"));
        assertEquals(LocalTime.of(13, 15), TimeDisplay.parse("13:15"));
        assertEquals(LocalTime.of(0, 0), TimeDisplay.parse("00:00"));
    }

    @Test
    void twelveAmIsMidnightAndTwelvePmIsNoon() {
        assertEquals(LocalTime.of(0, 0), TimeDisplay.parse("12:00 AM"));
        assertEquals(LocalTime.of(12, 0), TimeDisplay.parse("12:00 PM"));
    }

    @Test
    void refusesWhatCannotBeMeant() {
        assertNull(TimeDisplay.parse(null));
        assertNull(TimeDisplay.parse(""));
        assertNull(TimeDisplay.parse("   "));
        assertNull(TimeDisplay.parse("banana"));
        assertNull(TimeDisplay.parse("25:00"), "there is no 25th hour");
        assertNull(TimeDisplay.parse("9:75"), "there is no 75th minute");
        assertNull(TimeDisplay.parse("13:00 PM"), "13 PM is not a time anyone means");
        assertNull(TimeDisplay.parse("0:00 AM"), "a 12-hour clock has no hour zero");
        assertNull(TimeDisplay.parse("9:5"), "a single minute digit is a typo, not 5 past");
        assertNull(TimeDisplay.parse("9:00:00:00"));
    }
}
