package trippy.adapters.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.EventType;
import trippy.domain.valueobjects.IndoorOutdoorType;
import trippy.domain.valueobjects.Location;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The map joins the Day Plan's stops in order.
 *
 * <p>The numbered pins already carry the order, but a reader has to hop between them to see
 * the shape of the day. The line is what makes "this day crosses the city three times"
 * something the audience sees rather than something they are told.</p>
 */
class MapRouteLineTest {

    private static Activity place(String id, double lat, double lon) {
        return new Activity(id, id, ActivityCategory.ATTRACTION,
                new Location(lat, lon, id), 4.5, 60,
                LocalTime.of(9, 0), LocalTime.of(20, 0), IndoorOutdoorType.OUTDOOR, "none");
    }

    private static ScheduledEvent stop(Activity activity, int hour) {
        return new ScheduledEvent("event-" + activity.getId(), activity,
                LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), EventType.ACTIVITY, "");
    }

    private static MapPanel mapWith(List<Activity> places, List<ScheduledEvent> schedule) {
        MapPanel map = new MapPanel(600, 400);
        map.setActivities(places);
        map.setSchedule(schedule);
        return map;
    }

    @Test
    void theRouteFollowsTheDayPlanOrderNotTheOrderPlacesWereLoaded() {
        Activity west = place("accademia", 45.43137, 12.32809);
        Activity east = place("giardini", 45.42890, 12.35520);
        Activity centre = place("sanmarco", 45.43395, 12.33860);
        // Loaded west, east, centre -- but the day runs east, centre, west.
        MapPanel map = mapWith(Arrays.asList(west, east, centre),
                Arrays.asList(stop(east, 10), stop(centre, 12), stop(west, 15)));

        assertEquals(Arrays.asList("giardini", "sanmarco", "accademia"), map.routeOrder());
    }

    @Test
    void oneStopDrawsNoLine() {
        Activity only = place("sanmarco", 45.43395, 12.33860);
        MapPanel map = mapWith(Collections.singletonList(only),
                Collections.singletonList(stop(only, 10)));

        assertTrue(map.routeOrder().isEmpty(), "a single stop is a point, not a journey");
    }

    @Test
    void anEmptyDayPlanDrawsNoLine() {
        MapPanel map = mapWith(new ArrayList<>(), new ArrayList<>());

        assertTrue(map.routeOrder().isEmpty());
    }

    /**
     * A stop can be in the Day Plan while its place is not among the loaded map markers —
     * panning away drops it. The line then skips it rather than drawing to nowhere.
     */
    @Test
    void aScheduledStopWithNoLoadedPlaceIsSkipped() {
        Activity known = place("sanmarco", 45.43395, 12.33860);
        Activity offMap = place("faraway", 45.60000, 12.90000);
        MapPanel map = mapWith(Collections.singletonList(known),
                Arrays.asList(stop(offMap, 10), stop(known, 12)));

        assertTrue(map.routeOrder().isEmpty(),
                "only one stop is locatable, so there is no line to draw");
    }
}
