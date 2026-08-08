package closeai.infrastructure.places;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.domain.valueobjects.OpeningHours;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the parser makes of real {@code opening_hours} tags, and what it refuses to guess at.
 *
 * <p>The dates are chosen so the weekday is visible in the test rather than hidden in a
 * calendar lookup: 12 August 2026 is a Wednesday, 15 August a Saturday, 16 August a Sunday.</p>
 */
class OpeningHoursParserTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 8, 14);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 8, 15);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 8, 16);

    private static void assertSpans(List<OpeningHours.TimeInterval> intervals, String expected) {
        StringBuilder actual = new StringBuilder();
        for (OpeningHours.TimeInterval interval : intervals) {
            if (actual.length() > 0) {
                actual.append(",");
            }
            actual.append(interval);
        }
        assertEquals(expected, actual.toString());
    }

    @Test
    void aWeekdayRangeAppliesToEveryDayInIt() {
        OpeningHours hours = OpeningHoursParser.parse("Mo-Fr 09:00-17:00");

        assertTrue(hours.isKnown());
        assertSpans(hours.intervalsOn(WEDNESDAY), "09:00-17:00");
        assertTrue(hours.isClosedOn(SATURDAY), "the tag named only Monday to Friday");
    }

    @Test
    void aVenueThatShutsForLunchHasTwoSeparateWindows() {
        OpeningHours hours = OpeningHoursParser.parse("Mo-Su 09:00-12:00,13:00-17:00");

        assertSpans(hours.intervalsOn(WEDNESDAY), "09:00-12:00,13:00-17:00");
    }

    @Test
    void aDayMarkedOffIsClosedRatherThanUnknown() {
        OpeningHours hours = OpeningHoursParser.parse("Mo-Sa 10:00-18:00; Su off");

        assertTrue(hours.isKnown());
        assertTrue(hours.isClosedOn(SUNDAY));
        assertFalse(hours.isClosedOn(SATURDAY));
    }

    @Test
    void alaterRuleOverridesAnEarlierOneForTheDaysItNames() {
        OpeningHours hours = OpeningHoursParser.parse("Mo-Su 09:00-17:00; We 09:00-21:00");

        assertSpans(hours.intervalsOn(WEDNESDAY), "09:00-21:00");
        assertSpans(hours.intervalsOn(SATURDAY), "09:00-17:00");
    }

    @Test
    void aSpanPastMidnightIsSplitOntoBothDays() {
        OpeningHours hours = OpeningHoursParser.parse("Fr 20:00-02:00");

        assertSpans(hours.intervalsOn(FRIDAY), "20:00-23:59");
        assertSpans(hours.intervalsOn(SATURDAY), "00:00-02:00");
    }

    @Test
    void twentyFourSevenIsOpenRatherThanUnknown() {
        OpeningHours hours = OpeningHoursParser.parse("24/7");

        assertTrue(hours.isKnown());
        assertFalse(hours.isClosedOn(SUNDAY));
        assertFalse(hours.intervalsOn(SUNDAY).isEmpty());
    }

    @Test
    void midnightAsAClosingTimeBecomesTheLastMinuteOfTheDay() {
        assertSpans(OpeningHoursParser.parse("Mo-Su 18:00-24:00").intervalsOn(WEDNESDAY),
                "18:00-23:59");
    }

    @Test
    void aRuleWithNoWeekdayAppliesToTheWholeWeek() {
        OpeningHours hours = OpeningHoursParser.parse("08:00-20:00");

        assertSpans(hours.intervalsOn(WEDNESDAY), "08:00-20:00");
        assertSpans(hours.intervalsOn(SUNDAY), "08:00-20:00");
    }

    @Test
    void holidayRulesAreSkippedSoTheOrdinaryWeekSurvives() {
        OpeningHours hours = OpeningHoursParser.parse("Mo-Fr 09:00-17:00; PH off");

        assertTrue(hours.isKnown(), "one unsupported holiday rule must not lose the week");
        assertSpans(hours.intervalsOn(WEDNESDAY), "09:00-17:00");
    }

    /**
     * The important half of this class. Every one of these is a value the parser cannot be
     * sure about, and unknown is what keeps the scheduler permissive and honest instead of
     * inventing a constraint or a licence.
     */
    @Test
    void anythingNotFullyUnderstoodIsUnknownRatherThanGuessedAt() {
        String[] beyondUs = {
            null, "", "   ",
            "sunrise-sunset",
            "Jan-Mar 09:00-17:00",
            "Mo[1] 09:00-17:00",
            "Mo-Fr 09:00-17:00 \"ring the bell\"",
            "open when the owner is in",
            "Mo 25:00-30:00",
            "Mo 09:00",
            "Mo 09:00-09:00",
            "Xx 09:00-17:00",
        };
        for (String value : beyondUs) {
            assertFalse(OpeningHoursParser.parse(value).isKnown(),
                    "must not claim to know hours from: " + value);
        }
    }

    @Test
    void unknownHoursAreNotClosedHours() {
        OpeningHours hours = OpeningHoursParser.parse(null);

        assertFalse(hours.isKnown());
        assertFalse(hours.isClosedOn(WEDNESDAY),
                "silence from the provider must never read as a shut door");
        assertTrue(hours.intervalsOn(WEDNESDAY).isEmpty(),
                "and it offers no windows either, so callers must check isKnown first");
    }

    @Test
    void aSingleDayAndACommaListBothWork() {
        assertSpans(OpeningHoursParser.parse("We 11:00-15:00").intervalsOn(WEDNESDAY),
                "11:00-15:00");
        OpeningHours listed = OpeningHoursParser.parse("Mo,We,Fr 11:00-15:00");
        assertSpans(listed.intervalsOn(WEDNESDAY), "11:00-15:00");
        assertTrue(listed.isClosedOn(SATURDAY));
    }

    @Test
    void tagsAreReadWhateverTheirCasing() {
        assertSpans(OpeningHoursParser.parse("MO-FR 09:00-17:00").intervalsOn(WEDNESDAY),
                "09:00-17:00");
    }

    @Test
    void intervalsComeBackEarliestFirstEvenIfTheTagListsThemOutOfOrder() {
        assertSpans(OpeningHoursParser.parse("We 13:00-17:00,09:00-12:00")
                .intervalsOn(WEDNESDAY), "09:00-12:00,13:00-17:00");
    }

    @Test
    void aWindowStillEndsAfterItStarts() {
        for (OpeningHours.TimeInterval interval
                : OpeningHoursParser.parse("Fr 20:00-02:00").intervalsOn(FRIDAY)) {
            assertTrue(interval.getEnd().isAfter(interval.getStart()));
        }
        assertTrue(OpeningHoursParser.parse("24/7").intervalsOn(WEDNESDAY).get(0)
                .getStart().equals(LocalTime.MIN));
    }
}
