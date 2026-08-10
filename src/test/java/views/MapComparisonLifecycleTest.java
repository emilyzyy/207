package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;

/**
 * The map's Preview state: two routes while a proposal is on screen, one the rest of the time.
 *
 * <p>The comparison is presentation and it is temporary. Every way out of a Preview has to put
 * the map back — Apply, Cancel, a conflict, a different trip — and a second Preview has to
 * replace the two lines rather than leave the first pair underneath. Loaded search results,
 * centre and zoom are never touched, because redrawing a route is not a reason to throw away
 * everything the traveller has found.</p>
 */
class MapComparisonLifecycleTest {

    private static Activity place(String id, double latitude, double longitude) {
        return new Activity(id, "Place " + id, ActivityCategory.MUSEUM,
                new Location(latitude, longitude, id), 4.5, 60,
                LocalTime.of(8, 0), LocalTime.of(20, 0), IndoorOutdoorType.INDOOR, "none");
    }

    private static List<Activity> places() {
        List<Activity> all = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            all.add(place("a" + i, 43.60 + i * 0.01, -79.40 + i * 0.01));
        }
        return all;
    }

    private static MapPanel mapWithPlaces() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "these components need a display");
        MapPanel map = new MapPanel(600, 500);
        map.setActivities(places());
        return map;
    }

    @Test
    void anOrdinaryMapDrawsNoComparison() {
        MapPanel map = mapWithPlaces();

        assertFalse(map.isComparing(), "there is no proposal, so there is nothing to compare");
        assertTrue(map.proposedRouteOrder().isEmpty());
        assertTrue(map.beforeRouteOrder().isEmpty());
    }

    @Test
    void aPreviewDrawsBothTheSavedOrderAndTheProposedOrder() {
        MapPanel map = mapWithPlaces();

        map.showComparison(Arrays.asList("a0", "a1", "a2"), Arrays.asList("a2", "a0", "a1"));

        assertTrue(map.isComparing());
        assertEquals(Arrays.asList("a0", "a1", "a2"), map.beforeRouteOrder(),
                "red is the day as it stands");
        assertEquals(Arrays.asList("a2", "a0", "a1"), map.proposedRouteOrder(),
                "green is the proposal, in the proposal's own order");
    }

    /**
     * A Preview-only removal changes the proposal and nothing else, so only the green line may
     * move. The red line is the saved day, which the removal did not touch.
     */
    @Test
    void aDraftRemovalUpdatesOnlyTheProposedRoute() {
        MapPanel map = mapWithPlaces();
        map.showComparison(Arrays.asList("a0", "a1", "a2", "a3"),
                Arrays.asList("a0", "a1", "a2", "a3"));
        List<String> savedBefore = new ArrayList<>(map.beforeRouteOrder());

        map.showComparison(savedBefore, Arrays.asList("a0", "a2", "a3"));

        assertEquals(savedBefore, map.beforeRouteOrder(),
                "the saved day did not change, so neither may the red line");
        assertEquals(Arrays.asList("a0", "a2", "a3"), map.proposedRouteOrder());
    }

    @Test
    void cancellingRemovesBothComparisonLayers() {
        MapPanel map = mapWithPlaces();
        map.showComparison(Arrays.asList("a0", "a1"), Arrays.asList("a1", "a0"));

        map.clearComparison();

        assertFalse(map.isComparing(), "the ordinary map comes back");
        assertTrue(map.beforeRouteOrder().isEmpty(), "no faint red line is left behind");
        assertTrue(map.proposedRouteOrder().isEmpty(), "and no green one either");
    }

    /**
     * After Apply the proposal is simply the plan, and it is drawn like any other plan. Leaving
     * it green would keep telling the traveller they are looking at a proposal.
     */
    @Test
    void applyingLeavesTheOrdinaryRouteRatherThanAGreenOne() {
        MapPanel map = mapWithPlaces();
        map.showComparison(Arrays.asList("a0", "a1"), Arrays.asList("a1", "a0"));

        map.clearComparison();

        assertFalse(map.isComparing());
    }

    @Test
    void repeatedPreviewsReplaceTheLinesRatherThanStackThem() {
        MapPanel map = mapWithPlaces();

        for (int attempt = 0; attempt < 5; attempt++) {
            map.showComparison(Arrays.asList("a0", "a1", "a2"), Arrays.asList("a2", "a1", "a0"));
        }

        assertEquals(3, map.beforeRouteOrder().size(),
                "five attempts must leave one pair of lines, not five");
        assertEquals(3, map.proposedRouteOrder().size());
    }

    /**
     * Comparing must not disturb what the traveller has found or where they are looking.
     *
     * <p>Checked through what the map draws rather than through added accessors: the pins it
     * would paint, and the ordinary route order it would use, are both derived from the loaded
     * results and the current view.</p>
     */
    @Test
    void comparingPreservesTheLoadedResultsAndTheOrdinaryRoute() {
        MapPanel map = mapWithPlaces();
        map.setSchedule(scheduleOf("a0", "a1", "a2"));
        int placesBefore = map.markerDrawingOrder().size();
        List<String> ordinaryRouteBefore = new ArrayList<>(map.routeOrder());

        map.showComparison(Arrays.asList("a0", "a1", "a2"), Arrays.asList("a2", "a1", "a0"));
        map.clearComparison();

        assertEquals(placesBefore, map.markerDrawingOrder().size(),
                "every discovered place is still loaded");
        assertEquals(ordinaryRouteBefore, map.routeOrder(),
                "and the ordinary route is exactly what it was");
    }

    private static List<entity.entities.ScheduledEvent> scheduleOf(String... ids) {
        List<entity.entities.ScheduledEvent> events = new ArrayList<>();
        int hour = 9;
        for (String id : ids) {
            events.add(new entity.entities.ScheduledEvent(id, place(id, 43.60, -79.40),
                    LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0),
                    entity.valueobjects.EventType.ACTIVITY, ""));
            hour += 2;
        }
        return events;
    }

    /** An empty proposal is not a comparison; a conflict must leave the map alone. */
    @Test
    void anEmptyProposalDrawsNothing() {
        MapPanel map = mapWithPlaces();

        map.showComparison(Collections.emptyList(), Collections.emptyList());

        assertFalse(map.isComparing(),
                "a conflict has no proposal, so the map stays exactly as it was");
    }

    @Test
    void theBeforeRouteHasProjectorVisibleRedPixelsAndGreenWinsOnOverlap() {
        MapPanel map = new MapPanel(600, 500, false);
        map.setSize(600, 500);
        map.setActivities(places());
        map.showComparison(Arrays.asList("a0", "a2", "a4"), Collections.emptyList());

        BufferedImage beforeOnly = render(map);
        assertTrue(countDeepRed(beforeOnly, 0, beforeOnly.getHeight() - 55) > 20,
                "the dashed Before route must remain visibly coral over a pale map, not "
                        + "blend into ordinary roads");

        map.showComparison(Arrays.asList("a0", "a2", "a4"),
                Arrays.asList("a0", "a2", "a4"));
        BufferedImage overlapping = render(map);
        assertEquals(0, countDeepRed(overlapping, 0, overlapping.getHeight() - 55),
                "on a shared segment the solid green Proposed route must paint above red");
        assertTrue(countGreen(overlapping, 0, overlapping.getHeight() - 55) > 20);
    }

    private static BufferedImage render(MapPanel map) {
        BufferedImage image = new BufferedImage(
                map.getWidth(), map.getHeight(), BufferedImage.TYPE_INT_ARGB);
        map.paint(image.createGraphics());
        return image;
    }

    private static int countDeepRed(BufferedImage image, int fromY, int toY) {
        int count = 0;
        for (int y = fromY; y < toY; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                java.awt.Color colour = new java.awt.Color(image.getRGB(x, y), true);
                if (colour.getRed() > colour.getGreen() + 55
                        && colour.getGreen() < 130
                        && colour.getRed() > colour.getBlue() + 35) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countGreen(BufferedImage image, int fromY, int toY) {
        int count = 0;
        for (int y = fromY; y < toY; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                java.awt.Color colour = new java.awt.Color(image.getRGB(x, y), true);
                if (colour.getGreen() > colour.getRed() + 40
                        && colour.getGreen() > colour.getBlue() + 15) {
                    count++;
                }
            }
        }
        return count;
    }
}
