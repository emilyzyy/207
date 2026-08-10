package entity.valueobjects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The three states of {@link OpeningHours}, and specifically the distinction the rest of the
 * feature rests on: not knowing when a venue is open is not the same as knowing it is shut.
 */
class OpeningHoursTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 8, 15);

    private static OpeningHours wednesdayOnly(String... spans) {
        List<OpeningHours.TimeInterval> intervals = new ArrayList<>();
        for (String span : spans) {
            String[] halves = span.split("-");
            intervals.add(new OpeningHours.TimeInterval(
                    LocalTime.parse(halves[0]), LocalTime.parse(halves[1])));
        }
        Map<DayOfWeek, List<OpeningHours.TimeInterval>> week = new EnumMap<>(DayOfWeek.class);
        week.put(DayOfWeek.WEDNESDAY, intervals);
        return OpeningHours.of(week);
    }

    @Test
    void unknownHoursAreNeitherOpenNorClosed() {
        OpeningHours unknown = OpeningHours.unknown();

        assertFalse(unknown.isKnown());
        assertFalse(unknown.isClosedOn(WEDNESDAY),
                "no data must never be read as a shut door");
        assertTrue(unknown.intervalsOn(WEDNESDAY).isEmpty());
    }

    @Test
    void aNullMapIsUnknownRatherThanAnEmptyWeekOfClosures() {
        assertFalse(OpeningHours.of(null).isKnown());
    }

    @Test
    void aKnownWeekWithNoIntervalsForADayIsClosedThatDay() {
        OpeningHours hours = wednesdayOnly("10:00-16:00");

        assertTrue(hours.isKnown());
        assertFalse(hours.isClosedOn(WEDNESDAY));
        assertTrue(hours.isClosedOn(SATURDAY));
        assertTrue(hours.intervalsOn(SATURDAY).isEmpty());
    }

    @Test
    void alwaysOpenIsKnownAndNeverClosed() {
        OpeningHours always = OpeningHours.alwaysOpen();

        assertTrue(always.isKnown());
        assertFalse(always.isClosedOn(WEDNESDAY));
        assertEquals(LocalTime.MIN, always.intervalsOn(WEDNESDAY).get(0).getStart());
    }

    @Test
    void intervalsAreSortedEvenWhenSuppliedOutOfOrder() {
        OpeningHours hours = wednesdayOnly("14:00-18:00", "09:00-12:00");

        assertEquals(Arrays.asList("09:00-12:00", "14:00-18:00"),
                Arrays.asList(hours.intervalsOn(WEDNESDAY).get(0).toString(),
                        hours.intervalsOn(WEDNESDAY).get(1).toString()));
    }

    @Test
    void aNullDateYieldsNoIntervalsRatherThanAnException() {
        assertTrue(wednesdayOnly("10:00-16:00").intervalsOn(null).isEmpty());
    }

    /**
     * The invariant every consumer relies on. An interval that ended before it began, or one
     * of zero length, would let a placement "fit" inside nothing at all.
     */
    @Test
    void anIntervalMustEndAfterItStarts() {
        assertThrows(IllegalArgumentException.class, () -> new OpeningHours.TimeInterval(
                LocalTime.of(17, 0), LocalTime.of(9, 0)));
        assertThrows(IllegalArgumentException.class, () -> new OpeningHours.TimeInterval(
                LocalTime.of(9, 0), LocalTime.of(9, 0)));
        assertThrows(IllegalArgumentException.class, () -> new OpeningHours.TimeInterval(
                null, LocalTime.of(9, 0)));
        assertThrows(IllegalArgumentException.class, () -> new OpeningHours.TimeInterval(
                LocalTime.of(9, 0), null));
    }

    @Test
    void anIntervalReadsBackWhatItWasGiven() {
        OpeningHours.TimeInterval interval = new OpeningHours.TimeInterval(
                LocalTime.of(9, 30), LocalTime.of(17, 15));

        assertEquals(LocalTime.of(9, 30), interval.getStart());
        assertEquals(LocalTime.of(17, 15), interval.getEnd());
        assertEquals("09:30-17:15", interval.toString());
    }
}
