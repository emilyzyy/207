package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import interface_adapter.viewmodels.ImprovementView;

/**
 * Improvements are tiles, two to a row, however many of them there are.
 *
 * <p>The panel used to show four and account for the rest in a sentence, which first pointed
 * at a disclosure that had been removed and then said nothing useful at all. A fifth earned
 * improvement is worth the same as the fourth; it just wraps onto the next row.</p>
 *
 * <p>What is being pinned here is the shape — two columns, natural wrapping, the odd one out
 * on the left of its own last row — and that the tiles are the given improvements, in the
 * given order, each exactly once. Ordering is decided upstream and is not this panel's
 * business.</p>
 */
class ImprovementTileLayoutTest {

    private static List<ImprovementView> improvements(int count) {
        final List<ImprovementView> all = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            all.add(new ImprovementView("☀", "Improvement " + i, "Detail " + i));
        }
        return all;
    }

    private static ScheduleImprovementsPanel panelOf(List<ImprovementView> improvements)
            throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "building components needs a display");
        final ScheduleImprovementsPanel[] panel = new ScheduleImprovementsPanel[1];
        SwingUtilities.invokeAndWait(() ->
                panel[0] = new ScheduleImprovementsPanel(improvements));
        return panel[0];
    }

    /** The one container laid out as the two-column tile grid. */
    private static JPanel grid(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JPanel && ((JPanel) child).getLayout() instanceof GridLayout) {
                return (JPanel) child;
            }
            if (child instanceof Container) {
                final JPanel found = grid((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String textOf(Component component) {
        final StringBuilder text = new StringBuilder();
        collect(component, text);
        return text.toString();
    }

    private static void collect(Component component, StringBuilder text) {
        if (component instanceof JLabel) {
            text.append(((JLabel) component).getText()).append('\n');
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, text);
            }
        }
    }

    /** Rows the grid actually lays out, and the columns it lays them out in. */
    private static int[] shape(ScheduleImprovementsPanel panel) {
        final GridLayout layout = (GridLayout) grid(panel).getLayout();
        return new int[] {layout.getRows(), layout.getColumns()};
    }

    /** Tiles, ignoring the spacer that keeps an odd last tile from stretching. */
    private static List<Component> tiles(ScheduleImprovementsPanel panel) {
        final List<Component> tiles = new ArrayList<>();
        for (Component cell : grid(panel).getComponents()) {
            if (!textOf(cell).isEmpty()) {
                tiles.add(cell);
            }
        }
        return tiles;
    }

    @Test
    void oneImprovementIsOneTileAloneOnItsRow() throws Exception {
        final ScheduleImprovementsPanel panel = panelOf(improvements(1));

        assertEquals(1, shape(panel)[0], "one row");
        assertEquals(2, shape(panel)[1], "still a two-column grid");
        assertEquals(1, tiles(panel).size());
        // The spacer is what keeps it at half width on the left rather than stretched across.
        assertEquals(2, grid(panel).getComponentCount(), "tile plus spacer");
        assertFalse(textOf(panel).contains("Plus"), textOf(panel));
    }

    @Test
    void twoImprovementsFillOneRow() throws Exception {
        final ScheduleImprovementsPanel panel = panelOf(improvements(2));

        assertEquals(1, shape(panel)[0]);
        assertEquals(2, tiles(panel).size());
        assertEquals(2, grid(panel).getComponentCount(), "no spacer needed");
    }

    @Test
    void threeImprovementsStartASecondRow() throws Exception {
        final ScheduleImprovementsPanel panel = panelOf(improvements(3));

        assertEquals(2, shape(panel)[0]);
        assertEquals(3, tiles(panel).size());
        assertEquals(4, grid(panel).getComponentCount(), "third tile plus a spacer beside it");
    }

    /** The hero Preview: still exactly the 2x2 block it was. */
    @Test
    void fourImprovementsAreTwoRowsOfTwo() throws Exception {
        final ScheduleImprovementsPanel panel = panelOf(improvements(4));

        assertEquals(2, shape(panel)[0]);
        assertEquals(2, shape(panel)[1]);
        assertEquals(4, tiles(panel).size());
        assertEquals(4, grid(panel).getComponentCount(), "no spacer in a full block");
    }

    /** The fifth is a tile like any other, and it changes nothing above it. */
    @Test
    void fiveImprovementsAddAThirdRowWithoutDisturbingTheFirstFour() throws Exception {
        final ScheduleImprovementsPanel four = panelOf(improvements(4));
        final ScheduleImprovementsPanel five = panelOf(improvements(5));

        assertEquals(3, shape(five)[0], "a third row");
        assertEquals(2, shape(five)[1], "never four across");
        assertEquals(5, tiles(five).size());
        assertEquals(6, grid(five).getComponentCount(), "fifth tile plus a spacer beside it");

        for (int i = 0; i < 4; i++) {
            assertEquals(textOf(tiles(four).get(i)), textOf(tiles(five).get(i)),
                    "the first four tiles must be untouched by the fifth");
        }
        assertTrue(textOf(five).contains("Improvement 5"), "the fifth is drawn, not counted");
    }

    @Test
    void sixImprovementsAreThreeRowsOfTwo() throws Exception {
        final ScheduleImprovementsPanel panel = panelOf(improvements(6));

        assertEquals(3, shape(panel)[0]);
        assertEquals(2, shape(panel)[1]);
        assertEquals(6, tiles(panel).size());
        assertEquals(6, grid(panel).getComponentCount());
    }

    @Test
    void everyImprovementAppearsOnceInTheOrderGiven() throws Exception {
        for (int count : new int[] {1, 2, 3, 4, 5, 6, 7}) {
            final ScheduleImprovementsPanel panel = panelOf(improvements(count));
            final List<Component> tiles = tiles(panel);
            assertEquals(count, tiles.size(), "every earned improvement gets a tile");
            for (int i = 0; i < count; i++) {
                assertTrue(textOf(tiles.get(i)).contains("Improvement " + (i + 1)),
                        "tile " + i + " is out of order for a list of " + count);
            }
            final String text = textOf(panel);
            for (int i = 1; i <= count; i++) {
                assertEquals(1, occurrences(text, "Improvement " + i + "<"),
                        "Improvement " + i + " is duplicated");
            }
        }
    }

    @Test
    void noOverflowCopyOrRemovedDisclosureSurvivesAnywhere() throws Exception {
        for (int count : new int[] {1, 4, 5, 6, 9}) {
            final String text = textOf(panelOf(improvements(count)));
            assertFalse(text.contains("Plus"), text);
            assertFalse(text.contains("additional improvement"), text);
            assertFalse(text.contains("Why these changes?"), text);
            assertFalse(text.contains("Why this schedule?"), text);
            assertFalse(text.contains("more, under"), text);
        }
    }

    /**
     * Extra rows must grow the panel downwards only.
     *
     * <p>Width is what would push the chips and the footer off the side, so it is pinned at
     * both levels: the panel keeps its fixed sidebar width, and the grid inside it stays the
     * width it is at four tiles. Only the height is allowed to move.</p>
     */
    @Test
    void extraTilesAddHeightAndNeverWidth() throws Exception {
        final int fourWide = grid(panelOf(improvements(4))).getPreferredSize().width;
        final int fourHigh = panelOf(improvements(4)).getPreferredSize().height;

        for (int count : new int[] {1, 2, 3, 4, 5, 6, 9}) {
            final ScheduleImprovementsPanel panel = panelOf(improvements(count));
            assertEquals(ScheduleImprovementsPanel.PREFERRED_WIDTH,
                    panel.getPreferredSize().width,
                    "the sidebar width is fixed however many tiles there are");
            assertEquals(ScheduleImprovementsPanel.PREFERRED_WIDTH,
                    panel.getMaximumSize().width,
                    "and it may not be stretched wider by its host");
            assertEquals(fourWide, grid(panel).getPreferredSize().width,
                    "a " + count + "-tile grid must be no wider than the four-tile grid");
        }

        assertTrue(panelOf(improvements(6)).getPreferredSize().height > fourHigh,
                "six tiles are taller than four, which is where the room comes from");
        assertTrue(panelOf(improvements(5)).getPreferredSize().height > fourHigh,
                "and the fifth earns its own row rather than squeezing into the block");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            count++;
            at = text.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
