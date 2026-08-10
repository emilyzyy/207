package views;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class MapPanelViewportGridTest {

    private static String key(double[] cell) {
        return cell[0] + "," + cell[1] + "," + cell[2] + "," + cell[3];
    }

    @Test
    void pannedViewportsReuseGridAlignedCells() {
        // Two overlapping zoom-13-sized windows (lat 0.10° x lng 0.15°), panned by 0.04°.
        // Because cell edges snap to a fixed grid, the cells covering their common area must
        // have identical bounds, so the per-cell Overpass cache answers the second pan.
        final List<double[]> first = MapPanel.viewportCells(43.60, -79.50, 43.70, -79.35);
        final List<double[]> panned = MapPanel.viewportCells(43.64, -79.46, 43.74, -79.31);

        final Set<String> firstKeys = new HashSet<>();
        for (double[] cell : first) {
            firstKeys.add(key(cell));
        }
        int reused = 0;
        for (double[] cell : panned) {
            if (firstKeys.contains(key(cell))) {
                reused++;
            }
        }
        assertTrue(reused > 0, "panned viewport should reuse cells from the previous one");
    }

    @Test
    void snappedCellsAlwaysCoverTheVisibleBox() {
        final List<double[]> cells = MapPanel.viewportCells(43.601, -79.401, 43.699, -79.299);

        assertTrue(cells.size() <= 4);
        assertTrue(covers(cells, 43.601, -79.401));
        assertTrue(covers(cells, 43.699, -79.299));
        assertTrue(covers(cells, 43.601, -79.299));
        assertTrue(covers(cells, 43.699, -79.401));
        assertTrue(covers(cells, 43.65, -79.35));
    }

    @Test
    void wideViewportsStayWithinTheCellBudgetByDoublingCellSize() {
        final List<double[]> cells = MapPanel.viewportCells(43.40, -79.90, 43.90, -78.90);

        assertTrue(cells.size() <= 4);
        assertTrue(covers(cells, 43.40, -79.90));
        assertTrue(covers(cells, 43.90, -78.90));
    }

    private static boolean covers(List<double[]> cells, double lat, double lng) {
        for (double[] cell : cells) {
            if (lat >= cell[0] && lat <= cell[2] && lng >= cell[1] && lng <= cell[3]) {
                return true;
            }
        }
        return false;
    }
}
