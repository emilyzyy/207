package views;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import use_case.ports.ViewportPlacesLoader;

/** Pure Swing map panel that renders OpenStreetMap tiles with activity markers. */
public final class MapPanel extends JPanel {
    private static final int TILE_SIZE = 256;
    private static final int MIN_ZOOM = 2;
    private static final int MAX_ZOOM = 18;
    private static final int MAX_VIEWPORT_RESULTS = 25;
    /** Split the visible box into at most this many cells so places appear as each one answers. */
    private static final int MAX_VIEWPORT_CELLS = 4;
    /**
     * Viewport cells are snapped to a fixed lat/lng grid of roughly this size so panned views
     * reuse cells already fetched (the per-cell Overpass cache key is the cell's bounds).
     */
    private static final double VIEWPORT_CELL_SIZE_METERS = 3_000.0;
    /**
     * The lng step of the snap grid must not depend on a viewport's own latitude, or two panned
     * views would get different grid origins and never reuse cells. It is fixed at the reference
     * latitude (Toronto), which keeps every lng step a multiple of the same constant.
     */
    private static final double GRID_REFERENCE_METERS_PER_DEGREE_LNG =
            111_320.0 * Math.cos(Math.toRadians(43.6532));
    /** Background neighbor cells warmed after a viewport load settles, at most. */
    private static final int MAX_PREFETCH_CELLS = 3;
    /**
     * Prefetch only runs after the map has been idle this long. Warm-up requests to the public
     * Overpass replicas must never compete with the user's own pan/zoom queries, which the public
     * servers rate-limit aggressively.
     */
    private static final int PREFETCH_IDLE_DELAY_MILLIS = 2_000;
    private static final int MAX_GENERIC_MARKERS = 65;
    private static final int GENERIC_MARKER_RADIUS = 11;
    private static final int HIGHLIGHTED_MARKER_RADIUS = 14;
    private static final int SELECTED_MARKER_RADIUS = 18;
    private static final double DEFAULT_LAT = 43.6532;
    private static final double DEFAULT_LNG = -79.3832;
    private static final Color COLOR_PLAIN = new Color(0x1a, 0x56, 0xdb);
    private static final Color COLOR_BOOKMARKED = new Color(0x22, 0xa0, 0x6b);
    private static final Color COLOR_SCHEDULED = new Color(0xe0, 0xa0, 0x20);
    private static final Color COLOR_BOTH = new Color(0x7a, 0x5c, 0xd6);

    private double centerLat = DEFAULT_LAT;
    private double centerLng = DEFAULT_LNG;
    private int zoom = 13;
    private Point pressStart;
    private Point dragStart;
    private boolean isDragging;

    /** Muted so the numbered pins stay the thing you read first. */
    private static final Color ROUTE_COLOR = new Color(74, 134, 196, 150);

    /** The day as it stands: clearly coral, but dashed and lighter than the proposal. */
    private static final Color BEFORE_ROUTE_COLOR = new Color(183, 54, 70, 225);

    /** A subtle light edge keeps the dash readable across roads, parks and dark labels. */
    private static final Color BEFORE_ROUTE_HALO_COLOR = new Color(255, 255, 255, 175);

    private static final Stroke BEFORE_ROUTE_HALO_STROKE = new BasicStroke(
            6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            1f, new float[] {9f, 5f}, 0f);

    private static final Stroke BEFORE_ROUTE_STROKE = new BasicStroke(
            3.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            1f, new float[] {9f, 5f}, 0f);

    /** The proposal: the thing the traveller is being asked to look at. */
    private static final Color PROPOSED_ROUTE_COLOR = new Color(30, 150, 90, 235);

    private static final Stroke PROPOSED_ROUTE_STROKE = new BasicStroke(
            4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /** Unrelated discovery pins fade to this while a comparison is on screen. */
    private static final Color MUTED_PIN_COLOR = new Color(150, 160, 172, 90);

    private List<Activity> activities = new ArrayList<>();
    private String city = "the area";
    private Set<String> bookmarkedIds = Collections.emptySet();
    private Set<String> scheduledIds = Collections.emptySet();
    private List<String> scheduledActivityIds = Collections.emptyList();
    /** Non-empty only while a Preview is on screen; see {@link #showComparison}. */
    private List<String> beforeRoute = Collections.emptyList();
    private List<String> proposedRoute = Collections.emptyList();
    private String selectedActivityId = "";
    private boolean showHighlightedOnly = false;

    private static final double MAX_CITY_DISTANCE_METERS = 100_000;
    private boolean hasHomeLocation;
    private double homeLat;
    private double homeLng;
    private boolean hasFittedActivities;

    private final List<Integer> markerHitboxesX = new ArrayList<>();
    private final List<Integer> markerHitboxesY = new ArrayList<>();
    private final List<String> markerIds = new ArrayList<>();
    private java.util.function.Consumer<String> placeSelectionListener;
    private java.util.function.Consumer<List<Activity>> placesLoadedListener;
    private java.util.function.Consumer<Boolean> placesLoadingListener;

    private final JCheckBox highlightOnly =
            new JCheckBox("Bookmarked & added to plan only");

    private final Spinner loadingSpinner = new Spinner(34, 3f);

    private final ConcurrentHashMap<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    private final Set<String> pendingLoads = ConcurrentHashMap.newKeySet();
    private final ExecutorService tileLoader = Executors.newFixedThreadPool(2,
            r -> { final Thread t = new Thread(r, "TileLoader"); t.setDaemon(true); return t; });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(8)).build();
    private final boolean tileLoadingEnabled;

    private Timer viewportTimer;
    private Timer prefetchTimer;
    private ViewportRequest prefetchPending;
    private final ExecutorService viewportLoaderExecutor = Executors.newFixedThreadPool(3,
            r -> { final Thread t = new Thread(r, "ViewportLoader"); t.setDaemon(true); return t; });
    private final ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor(
            r -> { final Thread t = new Thread(r, "ViewportPrefetch"); t.setDaemon(true); return t; });

    private ViewportPlacesLoader viewportLoader;
    private boolean viewportLoadRunning;
    private ViewportRequest queuedViewportRequest;
    private String activeViewportKey = "";
    private String lastLoadedViewportKey = "";

    public MapPanel(int width, int height) {
        this(
                width,
                height,
                "osm".equalsIgnoreCase(
                        System.getProperty("trippy.map.tiles.mode", "osm")));
    }

    MapPanel(int width, int height, boolean tileLoadingEnabled) {
        this.tileLoadingEnabled = tileLoadingEnabled;
        setLayout(null);
        setPreferredSize(new Dimension(width, height));
        setOpaque(true);
        setBackground(new Color(232, 239, 244));
        setBorder(BorderFactory.createLineBorder(new Color(180, 200, 215), 1, true));
        setToolTipText(tileLoadingEnabled
                ? "OpenStreetMap tiles enabled"
                : "Offline map: markers are shown without network tiles");

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressStart = e.getPoint();
                dragStart = e.getPoint();
                isDragging = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMarkerClick(e);
                dragStart = null;
                isDragging = false;
                setCursor(Cursor.getDefaultCursor());
                scheduleViewportReload();
                repaint();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) return;
                final int dx = e.getX() - dragStart.x;
                final int dy = e.getY() - dragStart.y;
                final double px = latLngToPixelX(centerLng) - dx;
                final double py = latLngToPixelY(centerLat) - dy;
                centerLng = pixelXToLng(px);
                centerLat = pixelYToLat(py);
                dragStart = e.getPoint();
                repaint();
            }
        });
        addMouseWheelListener(e -> {
            final int oldZoom = zoom;
            zoom -= e.getWheelRotation();
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
            if (zoom != oldZoom) {
                final int w = getWidth(), h = getHeight();
                final double mouseLng = pixelXToLng(latLngToPixelX(centerLng) + (e.getX() - w / 2.0));
                final double mouseLat = pixelYToLat(latLngToPixelY(centerLat) + (e.getY() - h / 2.0));
                final double newMousePxX = latLngToPixelX(mouseLng);
                final double newMousePxY = latLngToPixelY(mouseLat);
                centerLng = pixelXToLng(newMousePxX - (e.getX() - w / 2.0));
                centerLat = pixelYToLat(newMousePxY - (e.getY() - h / 2.0));
                scheduleViewportReload();
                repaint();
            }
        });

        highlightOnly.setOpaque(false);
        highlightOnly.setFocusPainted(false);
        highlightOnly.setFont(SwingTheme.SMALL);
        highlightOnly.setForeground(Color.WHITE);
        highlightOnly.addActionListener(e -> {
            showHighlightedOnly = highlightOnly.isSelected();
            fitToActivities();
            repaint();
        });
        add(highlightOnly);
        loadingSpinner.setVisible(false);
        add(loadingSpinner);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                positionHighlightToggle();
                positionLoadingSpinner();
            }
        });
    }

    private void positionHighlightToggle() {
        final Dimension preferred = highlightOnly.getPreferredSize();
        final int x = Math.max(0, getWidth() - preferred.width - 12);
        highlightOnly.setBounds(x, 6, preferred.width, 24);
    }

    private void positionLoadingSpinner() {
        final Dimension size = loadingSpinner.getPreferredSize();
        loadingSpinner.setBounds(12, Math.max(0, getHeight() - size.height - 12),
                size.width, size.height);
    }

    public void setActivities(List<Activity> activities) {
        final List<Activity> newList = (activities == null) ? new ArrayList<>() : new ArrayList<>(activities);
        if (sameIds(newList, this.activities)) {
            repaint();
            return;
        }
        final boolean wasEmpty = this.activities.isEmpty();
        this.activities = newList;
        // Fit only the first time the map fills. A later empty->non-empty transition (places
        // briefly clearing during a refresh, then coming back) must not yank the user's view.
        if (wasEmpty && !this.activities.isEmpty() && !hasFittedActivities) {
            hasFittedActivities = true;
            fitToActivities();
        }
        repaint();
    }

    /** Sets the city name shown in the map overlay. */
    public void setCity(String city) {
        this.city = (city == null || city.trim().isEmpty()) ? "the area" : city.trim();
        repaint();
    }

    /** Sets which discovered places are bookmarked or scheduled so they can be highlighted. */
    public void setHighlightedIds(Set<String> bookmarked, Set<String> scheduled) {
        this.bookmarkedIds = (bookmarked == null) ? Collections.emptySet() : new HashSet<>(bookmarked);
        this.scheduledIds = (scheduled == null) ? Collections.emptySet() : new HashSet<>(scheduled);
        repaint();
    }

    /**
     * Draws the saved day and the proposal together, for as long as the Preview lasts.
     *
     * <p>Two lines answer "is this actually better?" faster than any number can: a day that
     * crosses the city three times looks like one, and the proposal comes out as a sweep. The
     * saved route is deliberately the quieter of the two — it is what the traveller already
     * knows; the green one is the thing being offered.</p>
     *
     * <p>This is presentation only, and temporary. No search results are discarded, no pins are
     * removed, the centre and zoom are untouched, and calling it again replaces the two lines
     * rather than adding a third. Nothing here writes to the itinerary.</p>
     */
    public void showComparison(List<String> savedOrder, List<String> proposedOrder) {
        beforeRoute = savedOrder == null ? Collections.emptyList() : new ArrayList<>(savedOrder);
        proposedRoute = proposedOrder == null
                ? Collections.emptyList() : new ArrayList<>(proposedOrder);
        repaint();
    }

    /** Returns to the ordinary single-route map. Safe to call when not comparing. */
    public void clearComparison() {
        beforeRoute = Collections.emptyList();
        proposedRoute = Collections.emptyList();
        repaint();
    }

    /** Whether the before/after comparison is currently drawn. */
    public boolean isComparing() {
        return !proposedRoute.isEmpty() || !beforeRoute.isEmpty();
    }

    /** The proposed order currently drawn in green, for tests and for the legend. */
    List<String> proposedRouteOrder() {
        return Collections.unmodifiableList(proposedRoute);
    }

    /** The saved order currently drawn in red. */
    List<String> beforeRouteOrder() {
        return Collections.unmodifiableList(beforeRoute);
    }

    /** Supplies ordered Day Plan activities so scheduled pins use itinerary numbering. */
    public void setSchedule(List<ScheduledEvent> events) {
        final List<String> ordered = new ArrayList<>();
        if (events != null) {
            for (ScheduledEvent event : events) {
                if (event.getActivity() != null
                        && !ordered.contains(event.getActivity().getId())) {
                    ordered.add(event.getActivity().getId());
                }
            }
        }
        scheduledActivityIds = ordered;
        scheduledIds = new HashSet<>(ordered);
        repaint();
    }

    /**
     * Selects and visually emphasizes one activity marker. The view centers on it only when the
     * selection actually changes: refresh flows (viewport merges, bookmark/day-plan updates) keep
     * re-applying the same selection, and re-flying on every one would yank the map back whenever
     * the user pans away from the clicked marker.
     */
    public void selectActivity(Activity activity) {
        final String nextId = activity == null ? "" : activity.getId();
        if (nextId.equals(selectedActivityId)) {
            repaint();
            return;
        }
        selectedActivityId = nextId;
        if (activity != null) {
            zoom = Math.max(zoom, 15);
            flyTo(activity.getLocation().getLatitude(), activity.getLocation().getLongitude());
        }
        else {
            repaint();
        }
    }

    private List<Activity> visibleActivities() {
        if (!showHighlightedOnly) return activities;
        final List<Activity> visible = new ArrayList<>();
        for (Activity activity : activities) {
            if (isHighlighted(activity.getId())
                    || activity.getId().equals(selectedActivityId)) {
                visible.add(activity);
            }
        }
        return visible;
    }

    private boolean isHighlighted(String id) {
        return bookmarkedIds.contains(id) || scheduledIds.contains(id);
    }

    private Color markerColor(String id) {
        final boolean bookmarked = bookmarkedIds.contains(id);
        final boolean scheduled = scheduledIds.contains(id);
        if (bookmarked && scheduled) return COLOR_BOTH;
        if (bookmarked) return COLOR_BOOKMARKED;
        if (scheduled) return COLOR_SCHEDULED;
        return COLOR_PLAIN;
    }

    private static boolean sameIds(List<Activity> a, List<Activity> b) {
        if (a.size() != b.size()) return false;
        final Set<String> idsB = new HashSet<>();
        for (Activity activity : b) idsB.add(activity.getId());
        for (Activity activity : a) {
            if (!idsB.contains(activity.getId())) return false;
        }
        return true;
    }

    public void flyTo(double lat, double lng) {
        centerLat = lat;
        centerLng = lng;
        // Programmatic navigation (initial city focus, marker selection) loads right away;
        // the debounce is only meant to let drag/zoom gestures settle.
        SwingUtilities.invokeLater(this::reloadViewport);
        repaint();
    }

    /** Sets the map viewport's place loader, enabling live load-as-you-navigate. */
    public void setViewportLoader(ViewportPlacesLoader loader) {
        this.viewportLoader = loader;
        // The initial city focus can happen before AppBuilder installs the live loader.
        // Request that already-visible viewport now instead of waiting for a pan or zoom.
        if (loader != null) SwingUtilities.invokeLater(this::reloadViewport);
    }

    /** Registers a callback invoked when the user clicks a marker on the map. */
    public void setPlaceSelectionListener(java.util.function.Consumer<String> listener) {
        this.placeSelectionListener = listener;
    }

    /** Registers a callback invoked with the full merged place list after a viewport reload. */
    public void setPlacesLoadedListener(java.util.function.Consumer<List<Activity>> listener) {
        this.placesLoadedListener = listener;
    }

    /** Registers a callback invoked when viewport place loading starts (true) or ends (false). */
    public void setPlacesLoadingListener(java.util.function.Consumer<Boolean> listener) {
        this.placesLoadingListener = listener;
    }

    private void handleMarkerClick(MouseEvent e) {
        if (placeSelectionListener == null) return;
        if (pressStart == null) return;
        final int dx = e.getX() - pressStart.x;
        final int dy = e.getY() - pressStart.y;
        if (Math.sqrt(dx * dx + dy * dy) > 6) return;
        // Hit-test from front to back, matching the reverse of the paint order.
        for (int i = markerHitboxesX.size() - 1; i >= 0; i--) {
            final int mx = markerHitboxesX.get(i);
            final int my = markerHitboxesY.get(i);
            if (Math.hypot(e.getX() - mx, e.getY() - my) <= 16) {
                placeSelectionListener.accept(markerIds.get(i));
                return;
            }
        }
    }

    private void scheduleViewportReload() {
        if (viewportLoader == null) return;
        cancelPrefetch();
        if (viewportTimer == null) {
            // Let pan/zoom settle for a bit before new places start loading, so a quick drag
            // doesn't fire a storm of viewport queries.
            viewportTimer = new Timer(1000, e -> reloadViewport());
            viewportTimer.setRepeats(false);
        }
        viewportTimer.restart();
    }

    void reloadViewport() {
        if (viewportLoader == null) return;
        cancelPrefetch();
        // Until the trip's destination is resolved the map still sits on the default center
        // (Toronto). Loading places there would pollute every itinerary with the wrong city,
        // so wait for the destination geocode before any viewport query runs.
        if (!hasHomeLocation) return;
        if (distanceToHomeMeters() > MAX_CITY_DISTANCE_METERS) return;
        final int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        final double[][] corners = visibleCoords(w, h);
        final int maxResults = maxResultsForZoom(zoom);
        final double south = corners[0][0];
        final double west = corners[0][1];
        final double north = corners[1][0];
        final double east = corners[1][1];
        ViewportRequest request = new ViewportRequest(
                south, west, north, east, maxResults, viewportKey(corners, maxResults));
        synchronized (this) {
            if (request.key.equals(activeViewportKey)
                    || request.key.equals(lastLoadedViewportKey)
                    || queuedViewportRequest != null
                    && request.key.equals(queuedViewportRequest.key)) {
                return;
            }
            queuedViewportRequest = request;
            if (viewportLoadRunning) return;
            request = takeQueuedViewportRequest();
        }
        notifyPlacesLoading(true);
        loadViewport(request);
    }

    private synchronized ViewportRequest takeQueuedViewportRequest() {
        final ViewportRequest request = queuedViewportRequest;
        queuedViewportRequest = null;
        viewportLoadRunning = true;
        activeViewportKey = request.key;
        return request;
    }

    private void loadViewport(ViewportRequest request) {
        final List<double[]> cells = viewportCells(request.south, request.west,
                request.north, request.east);
        final int perCell = Math.max(1,
                (int) Math.ceil(request.maxResults / (double) cells.size()));
        final AtomicInteger pending = new AtomicInteger(cells.size());
        for (double[] cell : cells) {
            final double cellSouth = cell[0];
            final double cellWest = cell[1];
            final double cellNorth = cell[2];
            final double cellEast = cell[3];
            viewportLoaderExecutor.submit(() -> {
                List<Activity> found = Collections.emptyList();
                try {
                    found = viewportLoader.load(cellSouth, cellWest,
                            cellNorth, cellEast, perCell);
                }
                catch (Exception ignored) {
                    // A failed public lookup leaves the current markers intact.
                }
                final List<Activity> completed = found;
                SwingUtilities.invokeLater(() -> {
                    // Places appear as each cell answers; a cell that finished after the user
                    // panned on is dropped so it cannot pollute the newer view.
                    final boolean current = activeViewportKey.equals(request.key);
                    final boolean last = current && pending.decrementAndGet() == 0;
                    if (current && completed != null && !completed.isEmpty()) {
                        mergeViewportResults(completed);
                    }
                    if (last) {
                        finishViewportLoad(request);
                    }
                });
            });
        }
    }

    private void finishViewportLoad(ViewportRequest completed) {
        ViewportRequest next = null;
        synchronized (this) {
            lastLoadedViewportKey = completed.key;
            viewportLoadRunning = false;
            activeViewportKey = "";
            if (queuedViewportRequest != null) next = takeQueuedViewportRequest();
        }
        if (next == null) {
            notifyPlacesLoading(false);
            schedulePrefetch(completed);
        }
        else {
            loadViewport(next);
        }
    }

    /**
     * Warm-up runs only once the map has sat idle for a moment. A user mid-pan or mid-zoom is
     * exactly when the public Overpass replicas are most likely to rate-limit, so the neighbor
     * cells wait until the interaction has stopped before they start asking for anything.
     */
    private void schedulePrefetch(ViewportRequest settled) {
        cancelPrefetch();
        prefetchPending = settled;
        prefetchTimer = new Timer(PREFETCH_IDLE_DELAY_MILLIS, e -> {
            prefetchNeighbours(prefetchPending);
            prefetchPending = null;
        });
        prefetchTimer.setRepeats(false);
        prefetchTimer.start();
    }

    /** Stops a scheduled warm-up so a new pan/zoom never finds prefetch in the way. */
    private void cancelPrefetch() {
        if (prefetchTimer != null) {
            prefetchTimer.stop();
            prefetchTimer = null;
        }
        prefetchPending = null;
    }

    /**
     * Warms the ring of cells just beyond the settled view so the next pan is answered from the
     * per-cell cache instead of fresh Overpass queries. Background work only: each cell checks
     * that no user-driven load has started, runs on its own single thread, and uses the same
     * per-cell result limit as the user's own load so a warm-up query is never heavier than a
     * pan would have made.
     */
    private void prefetchNeighbours(ViewportRequest settled) {
        if (viewportLoader == null || !hasHomeLocation) return;
        final GridCells grid = gridFor(settled.south, settled.west, settled.north, settled.east);
        final int perCell = Math.max(1,
                (int) Math.ceil(settled.maxResults / (double) grid.rows / grid.cols));
        final double marginLat = grid.stepLat;
        final double marginLng = grid.stepLng;
        final double south = Math.floor((settled.south - marginLat) / grid.stepLat) * grid.stepLat;
        final double west = Math.floor((settled.west - marginLng) / grid.stepLng) * grid.stepLng;
        final double north = Math.ceil((settled.north + marginLat) / grid.stepLat) * grid.stepLat;
        final double east = Math.ceil((settled.east + marginLng) / grid.stepLng) * grid.stepLng;
        int queued = 0;
        outer:
        for (double cellSouth = south; cellSouth < north; cellSouth += grid.stepLat) {
            for (double cellWest = west; cellWest < east; cellWest += grid.stepLng) {
                if (cellSouth < settled.north && cellWest < settled.east
                        && cellSouth + grid.stepLat > settled.south
                        && cellWest + grid.stepLng > settled.west) {
                    continue;
                }
                if (queued >= MAX_PREFETCH_CELLS) break outer;
                queued++;
                final double cSouth = cellSouth;
                final double cWest = cellWest;
                final double cNorth = cellSouth + grid.stepLat;
                final double cEast = cellWest + grid.stepLng;
                prefetchExecutor.submit(() ->
                        prefetchCell(cSouth, cWest, cNorth, cEast, perCell));
            }
        }
    }

    /** Loads one prefetch cell, dropping it the moment a user-driven load takes over. */
    private void prefetchCell(double south, double west, double north, double east, int perCell) {
        synchronized (this) {
            if (viewportLoadRunning || queuedViewportRequest != null) return;
        }
        try {
            viewportLoader.load(south, west, north, east, perCell);
        }
        catch (Exception ignored) {
            // A failed background prefetch must never disturb the map or the UI thread.
        }
    }

    /**
     * Splits the visible box into a few grid-aligned cells of about a discovery radius across,
     * capped so a fresh pan loads at most a handful of queries instead of one oversized
     * whole-box request. Cell edges snap to a fixed global grid, so two panned views that share a
     * cell produce identical bounds (and identical Overpass cache keys) instead of re-querying a
     * slightly shifted rectangle. The cell size doubles, keeping the grid alignment, until the
     * box fits within the cell budget.
     */
    static List<double[]> viewportCells(double south, double west,
                                        double north, double east) {
        final GridCells grid = gridFor(south, west, north, east);
        final List<double[]> cells = new ArrayList<>();
        for (int row = 0; row < grid.rows; row++) {
            for (int col = 0; col < grid.cols; col++) {
                cells.add(new double[]{
                        grid.south + row * grid.stepLat,
                        grid.west + col * grid.stepLng,
                        grid.south + (row + 1) * grid.stepLat,
                        grid.west + (col + 1) * grid.stepLng});
            }
        }
        return cells;
    }

    private static GridCells gridFor(double south, double west, double north, double east) {
        int scale = 1;
        while (true) {
            final double stepLat = VIEWPORT_CELL_SIZE_METERS * scale / 111_320.0;
            final double stepLng = VIEWPORT_CELL_SIZE_METERS * scale
                    / GRID_REFERENCE_METERS_PER_DEGREE_LNG;
            final double gridSouth = Math.floor(south / stepLat) * stepLat;
            final double gridWest = Math.floor(west / stepLng) * stepLng;
            final double gridNorth = Math.ceil(north / stepLat) * stepLat;
            final double gridEast = Math.ceil(east / stepLng) * stepLng;
            final int rows = Math.max(1, (int) Math.round((gridNorth - gridSouth) / stepLat));
            final int cols = Math.max(1, (int) Math.round((gridEast - gridWest) / stepLng));
            if (rows * cols <= MAX_VIEWPORT_CELLS) {
                return new GridCells(gridSouth, gridWest, rows, cols, stepLat, stepLng, scale);
            }
            scale *= 2;
        }
    }

    private static final class GridCells {
        final double south;
        final double west;
        final int rows;
        final int cols;
        final double stepLat;
        final double stepLng;
        final int scale;

        GridCells(double south, double west, int rows, int cols,
                  double stepLat, double stepLng, int scale) {
            this.south = south;
            this.west = west;
            this.rows = rows;
            this.cols = cols;
            this.stepLat = stepLat;
            this.stepLng = stepLng;
            this.scale = scale;
        }
    }

    private static String viewportKey(double[][] corners, int maxResults) {
        return Math.round(corners[0][0] * 100) + ","
                + Math.round(corners[0][1] * 100) + ","
                + Math.round(corners[1][0] * 100) + ","
                + Math.round(corners[1][1] * 100) + "," + maxResults;
    }

    private static final class ViewportRequest {
        private final double south;
        private final double west;
        private final double north;
        private final double east;
        private final int maxResults;
        private final String key;

        private ViewportRequest(double south, double west, double north, double east,
                                int maxResults, String key) {
            this.south = south;
            this.west = west;
            this.north = north;
            this.east = east;
            this.maxResults = maxResults;
            this.key = key;
        }
    }

    private void notifyPlacesLoading(boolean loading) {
        loadingSpinner.setVisible(loading);
        if (placesLoadingListener != null) {
            placesLoadingListener.accept(loading);
        }
    }

    private void mergeViewportResults(List<Activity> found) {
        final Map<String, Activity> byId = new HashMap<>();
        if (found != null) {
            for (Activity activity : found) {
                if (activity.getLocation() != null) byId.put(activity.getId(), activity);
            }
        }
        for (Activity activity : activities) {
            byId.putIfAbsent(activity.getId(), activity);
        }
        this.activities = new ArrayList<>(byId.values());
        repaint();
        if (placesLoadedListener != null && !byId.isEmpty()) {
            placesLoadedListener.accept(new ArrayList<>(this.activities));
        }
    }

    /** Computes the visible bounding box as {{south,west},{north,east}} for the current view. */
    private double[][] visibleCoords(int w, int h) {
        final double cx = latLngToPixelX(centerLng);
        final double cy = latLngToPixelY(centerLat);
        final double westPixel = cx - (w / 2.0);
        final double eastPixel = cx + (w / 2.0);
        final double northPixel = cy - (h / 2.0);
        final double southPixel = cy + (h / 2.0);
        double west = clampLng(pixelXToLng(westPixel));
        double east = clampLng(pixelXToLng(eastPixel));
        double north = pixelYToLat(visiblePixelY(northPixel));
        double south = pixelYToLat(visiblePixelY(southPixel));
        if (south > north) { final double t = south; south = north; north = t; }
        if (west > east) { final double t = east; east = west; west = t; }
        return new double[][]{{south, west}, {north, east}};
    }

    private static double clampLng(double lng) {
        return Math.max(-180.0, Math.min(180.0, lng));
    }

    /** Count scales with zoom: wider views (fewer places) vs. zoomed-in detail (more places). */
    private int maxResultsForZoom(int z) {
        return Math.max(20, Math.min(MAX_VIEWPORT_RESULTS, 8 + z * 4));
    }

    /** Clamps pixel-coordinate to keep the later inverse projection within valid latitude. */
    private double visiblePixelY(double py) {
        final double max = Math.pow(2, zoom) * TILE_SIZE;
        return Math.max(0.0, Math.min(max, py));
    }

    /** Centers the map on the given city, geocoding unknown cities asynchronously. */
    public void focusOnCity(String city) {
        StaticTileLoader.cityCoords(city).thenAccept(coords -> {
            if (coords != null) {
                homeLat = coords[0];
                homeLng = coords[1];
                hasHomeLocation = true;
                SwingUtilities.invokeLater(() -> flyTo(coords[0], coords[1]));
            }
        });
    }

    /** Establishes the trip's resolved home location and loads that viewport. */
    public void focusOnCoordinates(double latitude, double longitude) {
        homeLat = latitude;
        homeLng = longitude;
        hasHomeLocation = true;
        flyTo(latitude, longitude);
    }

    private double distanceToHomeMeters() {
        final double lat1 = Math.toRadians(centerLat);
        final double lat2 = Math.toRadians(homeLat);
        final double dLat = lat2 - lat1;
        final double dLng = Math.toRadians(homeLng - centerLng);
        final double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }

    public void fitAll() {
        fitToActivities();
    }

    public int getActivityCount() {
        return activities.size();
    }

    public boolean isTileLoadingEnabled() {
        return tileLoadingEnabled;
    }

    private void fitToActivities() {
        final List<Activity> visible = visibleActivities();
        if (visible.isEmpty()) return;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        for (Activity a : visible) {
            minLat = Math.min(minLat, a.getLocation().getLatitude());
            maxLat = Math.max(maxLat, a.getLocation().getLatitude());
            minLng = Math.min(minLng, a.getLocation().getLongitude());
            maxLng = Math.max(maxLng, a.getLocation().getLongitude());
        }
        centerLat = (minLat + maxLat) / 2;
        centerLng = (minLng + maxLng) / 2;
        final double latSpan = maxLat - minLat;
        final double lngSpan = maxLng - minLng;
        zoom = 13;
        final int pw = getWidth(), ph = getHeight();
        if (pw <= 0 || ph <= 0) return;
        for (int z = 17; z >= MIN_ZOOM; z--) {
            final double metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(centerLat)) / Math.pow(2, z);
            final double spanMeters = Math.max(latSpan * 111320.0, lngSpan * 111320.0 * Math.cos(Math.toRadians(centerLat)));
            if (spanMeters / metersPerPixel < Math.min(pw, ph) * 0.8) {
                zoom = z;
                break;
            }
        }
        repaint();
    }

    private double latLngToPixelX(double lng) {
        return (lng + 180.0) / 360.0 * Math.pow(2, zoom) * TILE_SIZE;
    }

    private double latLngToPixelY(double lat) {
        final double latRad = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0
                * Math.pow(2, zoom) * TILE_SIZE;
    }

    private double pixelXToLng(double px) {
        return px / (Math.pow(2, zoom) * TILE_SIZE) * 360.0 - 180.0;
    }

    private double pixelYToLat(double py) {
        final double n = 1.0 - 2.0 * py / (Math.pow(2, zoom) * TILE_SIZE);
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * n)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        final Graphics2D g2 = (Graphics2D) g;

        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        final double centerPixelX = latLngToPixelX(centerLng);
        final double centerPixelY = latLngToPixelY(centerLat);

        final int centerTileX = (int) Math.floor(centerPixelX / TILE_SIZE);
        final int centerTileY = (int) Math.floor(centerPixelY / TILE_SIZE);

        final int tilesX = (w / TILE_SIZE) + 3;
        final int tilesY = (h / TILE_SIZE) + 3;

        final int maxTiles = (int) Math.pow(2, zoom);
        final int currentZoom = zoom;

        for (int tx = -tilesX / 2; tx <= tilesX / 2; tx++) {
            for (int ty = -tilesY / 2; ty <= tilesY / 2; ty++) {
                final int cx = centerTileX + tx;
                final int cy = centerTileY + ty;
                if (cx < 0 || cx >= maxTiles || cy < 0 || cy >= maxTiles) continue;
                final int px = (int) (w / 2.0 + (cx * TILE_SIZE - centerPixelX));
                final int py = (int) (h / 2.0 + (cy * TILE_SIZE - centerPixelY));
                final String key = currentZoom + "/" + cx + "/" + cy;
                final BufferedImage tile = tileLoadingEnabled ? tileCache.get(key) : null;
                if (tile != null) {
                    g2.drawImage(tile, px, py, null);
                }
                else {
                    g2.setColor(new Color(230, 236, 242));
                    g2.fillRect(px, py, TILE_SIZE, TILE_SIZE);
                    g2.setColor(new Color(200, 210, 220));
                    g2.drawRect(px, py, TILE_SIZE - 1, TILE_SIZE - 1);
                    if (tileLoadingEnabled && !isDragging) {
                        loadTile(cx, cy, currentZoom, key);
                    }
                }
            }
        }

        g2.setColor(new Color(30, 40, 60, 200));
        g2.fillRect(0, 0, w, 36);

        final Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.setFont(SwingTheme.BODY);
        final int visibleCount = visibleActivities().size();
        final String title = visibleCount + " places in " + city
                + (tileLoadingEnabled ? " · OpenStreetMap" : " · offline map");
        g2.drawString(title, 12, 24);

        // Under the pins, so the numbers stay readable where the route doubles back.
        drawRoute(g2, w, h, centerPixelX, centerPixelY);
        drawMarkers(g2, w, h, centerPixelX, centerPixelY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    /**
     * Joins the Day Plan's stops in order, so the shape of the day is visible.
     *
     * <p>The numbered pins already say what order the day runs in, but a reader has to hop
     * between them to see it. Drawn as a line, a day that crosses the city three times looks
     * like a day that crosses the city three times — and after Autoschedule the same line
     * comes out as a sweep. That contrast is the clearest evidence the feature produces, and
     * it costs one polyline.</p>
     *
     * <p>Dashed and muted on purpose: this is context for the markers, not a route the user
     * should read as turn-by-turn directions. It is a straight line between stops, which is
     * not the path anyone walks — least of all in Venice.</p>
     */
    private void drawRoute(Graphics2D g2, int w, int h,
                           double centerPixelX, double centerPixelY) {
        if (isComparing()) {
            drawComparison(g2, w, h, centerPixelX, centerPixelY);
            return;
        }
        final List<int[]> points = routePoints(w, h, centerPixelX, centerPixelY);
        if (points.size() < 2) {
            return;
        }
        final Stroke oldStroke = g2.getStroke();
        g2.setColor(ROUTE_COLOR);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, new float[] {6f, 6f}, 0f));
        for (int i = 1; i < points.size(); i++) {
            final int[] from = points.get(i - 1);
            final int[] to = points.get(i);
            g2.drawLine(from[0], from[1], to[0], to[1]);
        }
        g2.setStroke(oldStroke);
    }

    /** The saved day dashed and secondary, the proposal solid and painted above it. */
    private void drawComparison(Graphics2D g2, int w, int h,
                                double centerPixelX, double centerPixelY) {
        final Stroke oldStroke = g2.getStroke();
        drawBeforePath(g2, pointsFor(beforeRoute, w, h, centerPixelX, centerPixelY));
        // Second, so where the two overlap the proposal is what is legible.
        drawPath(g2, pointsFor(proposedRoute, w, h, centerPixelX, centerPixelY),
                PROPOSED_ROUTE_COLOR, PROPOSED_ROUTE_STROKE);
        g2.setStroke(oldStroke);
        drawComparisonLegend(g2);
    }

    private void drawBeforePath(Graphics2D g2, List<int[]> points) {
        if (points.size() < 2) {
            return;
        }
        for (int i = 1; i < points.size(); i++) {
            final int[] from = points.get(i - 1);
            final int[] to = points.get(i);
            drawBeforeSegment(g2, from[0], from[1], to[0], to[1]);
        }
    }

    /** Used by both map and legend, so the key cannot drift from the real route style. */
    private void drawBeforeSegment(Graphics2D g2, int x1, int y1, int x2, int y2) {
        g2.setColor(BEFORE_ROUTE_HALO_COLOR);
        g2.setStroke(BEFORE_ROUTE_HALO_STROKE);
        g2.drawLine(x1, y1, x2, y2);
        g2.setColor(BEFORE_ROUTE_COLOR);
        g2.setStroke(BEFORE_ROUTE_STROKE);
        g2.drawLine(x1, y1, x2, y2);
    }

    private void drawPath(Graphics2D g2, List<int[]> points, Color colour, Stroke stroke) {
        if (points.size() < 2) {
            return;
        }
        g2.setColor(colour);
        g2.setStroke(stroke);
        for (int i = 1; i < points.size(); i++) {
            g2.drawLine(points.get(i - 1)[0], points.get(i - 1)[1],
                    points.get(i)[0], points.get(i)[1]);
        }
    }

    /** Small, flat, bottom-left: enough to name the two colours and nothing more. */
    private void drawComparisonLegend(Graphics2D g2) {
        final int boxWidth = 108;
        final int boxHeight = 42;
        final int x = 10;
        final int y = getHeight() - boxHeight - 10;
        g2.setColor(new Color(255, 255, 255, 225));
        g2.fillRoundRect(x, y, boxWidth, boxHeight, 8, 8);
        g2.setColor(new Color(216, 224, 232));
        g2.drawRoundRect(x, y, boxWidth, boxHeight, 8, 8);

        final Stroke oldStroke = g2.getStroke();
        g2.setFont(getFont().deriveFont(11f));
        drawBeforeSegment(g2, x + 8, y + 14, x + 30, y + 14);
        g2.setColor(new Color(60, 70, 82));
        g2.drawString("Before", x + 38, y + 18);

        g2.setStroke(PROPOSED_ROUTE_STROKE);
        g2.setColor(PROPOSED_ROUTE_COLOR);
        g2.drawLine(x + 8, y + 30, x + 30, y + 30);
        g2.setColor(new Color(60, 70, 82));
        g2.drawString("Proposed", x + 38, y + 34);
        g2.setStroke(oldStroke);
    }

    private List<int[]> pointsFor(List<String> order, int w, int h,
                                  double centerPixelX, double centerPixelY) {
        final List<int[]> points = new ArrayList<>();
        for (String id : order) {
            for (Activity activity : activities) {
                if (!activity.getId().equals(id) || activity.getLocation() == null) {
                    continue;
                }
                final double px = latLngToPixelX(activity.getLocation().getLongitude());
                final double py = latLngToPixelY(activity.getLocation().getLatitude());
                points.add(new int[] {
                        (int) (w / 2.0 + (px - centerPixelX)),
                        (int) (h / 2.0 + (py - centerPixelY))});
                break;
            }
        }
        return points;
    }

    /** Screen positions of the scheduled stops, in Day Plan order. */
    private List<int[]> routePoints(int w, int h, double centerPixelX, double centerPixelY) {
        final List<int[]> points = new ArrayList<>();
        for (String id : scheduledActivityIds) {
            for (Activity activity : activities) {
                if (!activity.getId().equals(id) || activity.getLocation() == null) {
                    continue;
                }
                final double px = latLngToPixelX(activity.getLocation().getLongitude());
                final double py = latLngToPixelY(activity.getLocation().getLatitude());
                points.add(new int[] {
                        (int) (w / 2.0 + (px - centerPixelX)),
                        (int) (h / 2.0 + (py - centerPixelY))});
                break;
            }
        }
        return points;
    }

    /** The stops the route line would join, in order; empty when fewer than two are known. */
    List<String> routeOrder() {
        final List<String> ordered = new ArrayList<>();
        for (String id : scheduledActivityIds) {
            for (Activity activity : activities) {
                if (activity.getId().equals(id)) {
                    ordered.add(id);
                    break;
                }
            }
        }
        return ordered.size() < 2 ? new ArrayList<>() : ordered;
    }

    private void drawMarkers(Graphics2D g2, int w, int h,
                             double centerPixelX, double centerPixelY) {
        markerHitboxesX.clear();
        markerHitboxesY.clear();
        markerIds.clear();
        for (Activity a : markerDrawingOrder()) {
            final double lng = a.getLocation().getLongitude();
            final double lat = a.getLocation().getLatitude();
            final double markerPxX = latLngToPixelX(lng);
            final double markerPxY = latLngToPixelY(lat);
            final int sx = (int) (w / 2.0 + (markerPxX - centerPixelX));
            final int sy = (int) (h / 2.0 + (markerPxY - centerPixelY));
            if (sx < -30 || sx > w + 30 || sy < -30 || sy > h + 30) {
                continue;
            }
            markerHitboxesX.add(sx);
            markerHitboxesY.add(sy);
            markerIds.add(a.getId());
            drawMarker(g2, a.getId(), sx, sy);
            // At city-level zoom, generic labels recreate the same clutter as oversized pins.
            // Important markers remain labelled; zooming in reveals ordinary place names.
            if (markerLayer(a.getId()) > 0 || zoom >= 16) {
                g2.setColor(new Color(0x1a, 0x1f, 0x36));
                g2.setFont(SwingTheme.SMALL);
                String name = a.getName();
                if (name.length() > 25) name = name.substring(0, 22) + "...";
                g2.drawString(name, sx + markerRadius(a.getId()) + 4, sy + 4);
            }
        }
    }

    /** Back-to-front marker order: generic, bookmarked, planned, then selected. */
    List<Activity> markerDrawingOrder() {
        final List<Activity> ordered = new ArrayList<>(visibleActivities());
        ordered.sort((left, right) -> Integer.compare(
                markerLayer(left.getId()), markerLayer(right.getId())));
        final List<Activity> limited = new ArrayList<>();
        int genericCount = 0;
        for (Activity activity : ordered) {
            if (markerLayer(activity.getId()) == 0
                    && genericCount++ >= MAX_GENERIC_MARKERS) {
                continue;
            }
            limited.add(activity);
        }
        return limited;
    }

    private int markerLayer(String activityId) {
        if (activityId.equals(selectedActivityId)) return 3;
        if (scheduledIds.contains(activityId)) return 2;
        if (bookmarkedIds.contains(activityId)) return 1;
        return 0;
    }

    private void drawMarker(Graphics2D g2, String activityId, int sx, int sy) {
        // While a comparison is on screen, a pin that is in neither route is context rather
        // than content: faded to a dot so the two lines stay readable through a field of blue.
        // The pins themselves are untouched — this is how they are drawn, not what they are.
        if (isComparing() && !beforeRoute.contains(activityId)
                && !proposedRoute.contains(activityId)
                && !activityId.equals(selectedActivityId)) {
            g2.setColor(MUTED_PIN_COLOR);
            g2.fillOval(sx - 3, sy - 3, 6, 6);
            return;
        }
        final boolean selected = activityId.equals(selectedActivityId);
        final boolean bookmarked = bookmarkedIds.contains(activityId);
        final int scheduleIndex = scheduledActivityIds.indexOf(activityId);
        final int radius = markerRadius(activityId);

        if (selected) {
            g2.setColor(new Color(255, 255, 255, 225));
            g2.fillOval(sx - radius - 5, sy - radius - 5,
                    (radius + 5) * 2, (radius + 5) * 2);
            g2.setColor(SwingTheme.NAVY);
            g2.drawOval(sx - radius - 5, sy - radius - 5,
                    (radius + 5) * 2, (radius + 5) * 2);
        }

        g2.setColor(new Color(0, 0, 0, 45));
        g2.fillOval(sx - radius + 1, sy - radius + 4, radius * 2, radius * 2);
        g2.setColor(markerColor(activityId));
        g2.fillPolygon(new Polygon(
                new int[]{sx - 7, sx + 7, sx},
                new int[]{sy + radius - 5, sy + radius - 5, sy + radius + 9}, 3));
        g2.fillOval(sx - radius, sy - radius, radius * 2, radius * 2);

        if (scheduleIndex >= 0) {
            final String label = String.valueOf(scheduleIndex + 1);
            g2.setColor(Color.WHITE);
            g2.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
            final int textWidth = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, sx - textWidth / 2, sy + 5);
            if (bookmarked) {
                drawBookmarkBadge(g2, sx + radius - 4, sy - radius + 3);
            }
        }
        else if (bookmarked) {
            drawBookmarkGlyph(g2, sx, sy, radius);
        }
        else {
            g2.setColor(Color.WHITE);
            g2.fillOval(sx - 4, sy - 4, 8, 8);
        }
    }

    /** Ordinary discovery pins are deliberately quieter than user-selected map content. */
    int markerRadius(String activityId) {
        if (activityId.equals(selectedActivityId)) return SELECTED_MARKER_RADIUS;
        if (isHighlighted(activityId)) return HIGHLIGHTED_MARKER_RADIUS;
        return GENERIC_MARKER_RADIUS;
    }

    private void drawBookmarkGlyph(Graphics2D g2, int sx, int sy, int radius) {
        final int width = Math.max(10, radius - 3);
        final int height = Math.max(15, radius + 3);
        final int left = sx - width / 2;
        final int top = sy - height / 2;
        final Polygon ribbon = new Polygon(
                new int[]{left, left + width, left + width, sx, left},
                new int[]{top, top, top + height, top + height - 5, top + height}, 5);
        g2.setColor(Color.WHITE);
        g2.fillPolygon(ribbon);
    }

    private void drawBookmarkBadge(Graphics2D g2, int sx, int sy) {
        g2.setColor(COLOR_BOOKMARKED);
        g2.fillOval(sx - 7, sy - 7, 14, 14);
        g2.setColor(Color.WHITE);
        final Polygon ribbon = new Polygon(
                new int[]{sx - 3, sx + 3, sx + 3, sx, sx - 3},
                new int[]{sy - 4, sy - 4, sy + 4, sy + 1, sy + 4}, 5);
        g2.fillPolygon(ribbon);
    }

    String markerText(String activityId) {
        final int index = scheduledActivityIds.indexOf(activityId);
        if (index >= 0) return String.valueOf(index + 1);
        return bookmarkedIds.contains(activityId) ? "bookmark" : "pin";
    }

    public String getSelectedActivityId() {
        return selectedActivityId;
    }

    private void loadTile(int x, int y, int z, String key) {
        if (tileCache.containsKey(key) || !pendingLoads.add(key)) return;
        tileLoader.submit(() -> {
            try {
                final String url = "https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png";
                final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "Trippy-CSC207/1.0")
                        .GET().build();
                final HttpResponse<InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    final BufferedImage img = ImageIO.read(response.body());
                    if (img != null) {
                        tileCache.put(key, img);
                        SwingUtilities.invokeLater(this::repaint);
                    }
                }
                response.body().close();
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            catch (Exception ignored) {
            }
            finally {
                pendingLoads.remove(key);
            }
        });
    }
}
