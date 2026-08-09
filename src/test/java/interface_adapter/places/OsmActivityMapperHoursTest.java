package interface_adapter.places;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import entity.entities.Activity;

/**
 * Places discovered through the OSM mapper must arrive with their hours <em>parsed</em>,
 * not merely quoted.
 *
 * <p>This is the regression that matters: the mapper used the constructor that records the
 * raw tag and leaves the per-weekday reading unknown. Everything still compiled, every test
 * still passed, and the whole opening-hours constraint quietly stopped applying — a venue
 * that is shut becomes schedulable, and a pin inside a closure stops being a conflict.</p>
 */
class OsmActivityMapperHoursTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);

    private static Activity mapOne(String openingHours) throws Exception {
        String json = "{\"elements\":[{\"type\":\"node\",\"id\":1,\"lat\":45.44,\"lon\":12.32,"
                + "\"tags\":{\"name\":\"Test Place\",\"amenity\":\"restaurant\""
                + (openingHours == null ? ""
                        : ",\"opening_hours\":\"" + openingHours + "\"")
                + "}}]}";
        JsonNode element = new ObjectMapper().readTree(json).get("elements").get(0);
        Optional<Activity> mapped = new OsmActivityMapper(new ObjectMapper()).fromOverpass(element);
        assertTrue(mapped.isPresent(), "the mapper should have produced a place");
        return mapped.get();
    }

    @Test
    void aSplitHoursTagArrivesParsedIntoItsTwoIntervals() throws Exception {
        Activity place = mapOne("Mo-Sa 12:30-14:30,19:00-22:30");

        assertEquals("Mo-Sa 12:30-14:30,19:00-22:30", place.getOpeningHoursText());
        assertTrue(place.getOpeningHours().isKnown(),
                "the raw tag alone is not enough; the scheduler needs the parsed reading");
        assertEquals(2, place.getOpeningHours().intervalsOn(WEDNESDAY).size());
        assertEquals(LocalTime.of(14, 30),
                place.getOpeningHours().intervalsOn(WEDNESDAY).get(0).getEnd());
    }

    @Test
    void aVenueClosedOnADayIsKnownToBeClosed() throws Exception {
        Activity place = mapOne("Mo-Tu,Th-Su 07:00-20:00; We closed");

        assertTrue(place.getOpeningHours().isClosedOn(WEDNESDAY),
                "a venue shut on the trip date must be schedulable-as-closed, not unknown");
    }

    @Test
    void noTagStillMeansUnknownRatherThanClosed() throws Exception {
        Activity place = mapOne(null);

        assertFalse(place.getOpeningHours().isKnown());
        assertFalse(place.getOpeningHours().isClosedOn(WEDNESDAY),
                "most OSM places publish no hours, and none of them are therefore shut");
    }
}
