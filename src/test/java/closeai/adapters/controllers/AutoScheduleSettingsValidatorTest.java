package closeai.adapters.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutoScheduleSettingsValidatorTest {

    private static final LocalTime TRIP_START = LocalTime.of(9, 0);
    private static final LocalTime TRIP_END = LocalTime.of(21, 0);

    private final AutoScheduleSettingsValidator validator = new AutoScheduleSettingsValidator();

    private static AutoScheduleSettings settings(LocalTime from, LocalTime until,
                                                 AutoScheduleSettings.Window... windows) {
        return new AutoScheduleSettings(from, until, TransportationMode.WALKING,
                Arrays.asList(windows), true, true);
    }

    @Test
    void sensibleSettingsPass() {
        List<String> problems = validator.validate(
                settings(LocalTime.of(10, 0), LocalTime.of(18, 0),
                        new AutoScheduleSettings.Window(LocalTime.of(12, 0), LocalTime.of(13, 0))),
                TRIP_START, TRIP_END);

        assertTrue(problems.isEmpty(), "unexpected problems: " + problems);
    }

    @Test
    void anEndBeforeAStartIsRejected() {
        List<String> problems = validator.validate(
                settings(LocalTime.of(18, 0), LocalTime.of(10, 0)), TRIP_START, TRIP_END);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("later than"));
    }

    @Test
    void hoursWiderThanTheTripAreRejected() {
        List<String> problems = validator.validate(
                settings(LocalTime.of(6, 0), LocalTime.of(23, 0)), TRIP_START, TRIP_END);

        assertTrue(problems.get(0).contains("within the trip's hours"));
    }

    @Test
    void anUnavailablePeriodOutsideTheAvailableHoursIsRejected() {
        List<String> problems = validator.validate(
                settings(LocalTime.of(10, 0), LocalTime.of(18, 0),
                        new AutoScheduleSettings.Window(LocalTime.of(19, 0), LocalTime.of(20, 0))),
                TRIP_START, TRIP_END);

        assertTrue(problems.get(0).contains("outside the hours you are available"));
    }

    @Test
    void anUnavailablePeriodThatEndsBeforeItStartsIsRejected() {
        List<String> problems = validator.validate(
                settings(LocalTime.of(10, 0), LocalTime.of(18, 0),
                        new AutoScheduleSettings.Window(LocalTime.of(14, 0), LocalTime.of(13, 0))),
                TRIP_START, TRIP_END);

        assertTrue(problems.get(0).contains("must end after it starts"));
    }

    @Test
    void overlappingUnavailablePeriodsAreSurfacedRatherThanMerged() {
        List<String> problems = validator.validate(
                settings(LocalTime.of(10, 0), LocalTime.of(18, 0),
                        new AutoScheduleSettings.Window(LocalTime.of(12, 0), LocalTime.of(14, 0)),
                        new AutoScheduleSettings.Window(LocalTime.of(13, 0), LocalTime.of(15, 0))),
                TRIP_START, TRIP_END);

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("overlap")),
                "an overlap is usually a typo and the traveller should be told");
    }

    @Test
    void adjacentUnavailablePeriodsAreFine() {
        List<String> problems = validator.validate(
                settings(LocalTime.of(10, 0), LocalTime.of(18, 0),
                        new AutoScheduleSettings.Window(LocalTime.of(12, 0), LocalTime.of(13, 0)),
                        new AutoScheduleSettings.Window(LocalTime.of(13, 0), LocalTime.of(14, 0))),
                TRIP_START, TRIP_END);

        assertTrue(problems.isEmpty(), "one ending as the next begins is not an overlap");
    }

    @Test
    void missingTimesAreReportedBeforeAnythingElse() {
        List<String> problems = validator.validate(
                new AutoScheduleSettings(null, null, TransportationMode.WALKING,
                        Collections.emptyList(), true, true),
                TRIP_START, TRIP_END);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("available-from"));
    }

    @Test
    void anUnknownTripWindowSimplySkipsThatCheck() {
        List<String> problems = validator.validate(
                settings(LocalTime.of(6, 0), LocalTime.of(23, 0)), null, null);

        assertTrue(problems.isEmpty());
    }
}
