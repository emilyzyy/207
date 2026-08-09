package views;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Point;
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
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Pure Swing map panel that renders OpenStreetMap tiles with activity markers. */
public final class MapPanel extends JPanel {
    private static final int TILE_SIZE = 256;
    private static final int MIN_ZOOM = 2;
    private static final int MAX_ZOOM = 18;
    private static final int MAX_VIEWPORT_RESULTS = 75;
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

    private List<Activity> activities = new ArrayList<>();
    private String city = "the area";
    private Set<String> bookmarkedIds = Collections.emptySet();
    private Set<String> scheduledIds = Collections.emptySet();
    private List<String> scheduledActivityIds = Collections.emptyList();
    private String selectedActivityId = "";
    private boolean showHighlightedOnly = false;

    private static final double MAX_CITY_DISTANCE_METERS = 100_000;
    private boolean hasHomeLocation;
    private double homeLat;
    private double homeLng;

    private final List<Integer> markerHitboxesX = new ArrayList<>();
    private final List<Integer> markerHitboxesY = new ArrayList<>();
    private final List<String> markerIds = new ArrayList<>();
    private java.util.function.Consumer<String> placeSelectionListener;
    private java.util.function.Consumer<List<Activity>> placesLoadedListener;
    private java.util.function.Consumer<Boolean> placesLoadingListener;

    private final JCheckBox highlightOnly =
            new JCheckBox("Bookmarked & added to plan only");

    private final ConcurrentHashMap<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    private final Set<String> pendingLoads = ConcurrentHashMap.newKeySet();
    private final ExecutorService tileLoader = Executors.newFixedThreadPool(2,
            r -> { Thread t = new Thread(r, "TileLoader"); t.setDaemon(true); return t; });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(8)).build();
    private final boolean tileLoadingEnabled;

    private Timer viewportTimer;
    private final ExecutorService viewportLoaderExecutor = Executors.newSingleThreadExecutor(
            r -> { Thread t = new Thread(r, "ViewportLoader"); t.setDaemon(true); return t; });

    private ViewportPlacesLoader viewportLoader;
    private boolean viewportLoadRunning;
    private ViewportRequest queuedViewportRequest;
    private String activeViewportKey = "";
    private String lastLoadedViewportKey = "";

    /**
     * Supplies places for a visible map window. Implementations should perform blocking
     * lookups off the Swing event-dispatch thread (the loader is invoked on a worker thread).
     */
    public interface ViewportPlacesLoader {
        List<Activity> load(double south, double west, double north, double east, int maxResults);
    }

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
                int dx = e.getX() - dragStart.x;
                int dy = e.getY() - dragStart.y;
                double px = latLngToPixelX(centerLng) - dx;
                double py = latLngToPixelY(centerLat) - dy;
                centerLng = pixelXToLng(px);
                centerLat = pixelYToLat(py);
                dragStart = e.getPoint();
                repaint();
            }
        });
        addMouseWheelListener(e -> {
            int oldZoom = zoom;
            zoom -= e.getWheelRotation();
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
            if (zoom != oldZoom) {
                int w = getWidth(), h = getHeight();
                double mouseLng = pixelXToLng(latLngToPixelX(centerLng) + (e.getX() - w / 2.0));
                double mouseLat = pixelYToLat(latLngToPixelY(centerLat) + (e.getY() - h / 2.0));
                double newMousePxX = latLngToPixelX(mouseLng);
                double newMousePxY = latLngToPixelY(mouseLat);
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
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                positionHighlightToggle();
            }
        });
    }

    private void positionHighlightToggle() {
        Dimension preferred = highlightOnly.getPreferredSize();
        int x = Math.max(0, getWidth() - preferred.width - 12);
        highlightOnly.setBounds(x, 6, preferred.width, 24);
    }

    public void setActivities(List<Activity> activities) {
        List<Activity> newList = (activities == null) ? new ArrayList<>() : new ArrayList<>(activities);
        if (sameIds(newList, this.activities)) {
            repaint();
            return;
        }
        boolean wasEmpty = this.activities.isEmpty();
        this.activities = newList;
        if (wasEmpty && !this.activities.isEmpty()) fitToActivities();
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

    /** Supplies ordered Day Plan activities so scheduled pins use itinerary numbering. */
    public void setSchedule(List<ScheduledEvent> events) {
        List<String> ordered = new ArrayList<>();
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

    /** Selects, centers, and visually emphasizes one activity marker. */
    public void selectActivity(Activity activity) {
        selectedActivityId = activity == null ? "" : activity.getId();
        if (activity != null) {
            zoom = Math.max(zoom, 15);
            flyTo(activity.getLocation().getLatitude(), activity.getLocation().getLongitude());
        } else {
            repaint();
        }
    }

    private List<Activity> visibleActivities() {
        if (!showHighlightedOnly) return activities;
        List<Activity> visible = new ArrayList<>();
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
        boolean bookmarked = bookmarkedIds.contains(id);
        boolean scheduled = scheduledIds.contains(id);
        if (bookmarked && scheduled) return COLOR_BOTH;
        if (bookmarked) return COLOR_BOOKMARKED;
        if (scheduled) return COLOR_SCHEDULED;
        return COLOR_PLAIN;
    }

    private static boolean sameIds(List<Activity> a, List<Activity> b) {
        if (a.size() != b.size()) return false;
        Set<String> idsB = new HashSet<>();
        for (Activity activity : b) idsB.add(activity.getId());
        for (Activity activity : a) {
            if (!idsB.contains(activity.getId())) return false;
        }
        return true;
    }

    public void flyTo(double lat, double lng) {
        centerLat = lat;
        centerLng = lng;
        scheduleViewportReload();
        repaint();
    }

    /** Sets the map viewport's place loader, enabling live load-as-you-navigate. */
    public void setViewportLoader(ViewportPlacesLoader loader) {
        this.viewportLoader = loader;
        // The initial city focus can happen before AppBuilder installs the live loader.
        // Request that already-visible viewport now instead of waiting for a pan or zoom.
        if (loader != null) SwingUtilities.invokeLater(this::scheduleViewportReload);
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
        int dx = e.getX() - pressStart.x;
        int dy = e.getY() - pressStart.y;
        if (Math.sqrt(dx * dx + dy * dy) > 6) return;
        // Hit-test from front to back, matching the reverse of the paint order.
        for (int i = markerHitboxesX.size() - 1; i >= 0; i--) {
            int mx = markerHitboxesX.get(i);
            int my = markerHitboxesY.get(i);
            if (Math.hypot(e.getX() - mx, e.getY() - my) <= 16) {
                placeSelectionListener.accept(markerIds.get(i));
                return;
            }
        }
    }

    private void scheduleViewportReload() {
        if (viewportLoader == null) return;
        if (viewportTimer == null) {
            viewportTimer = new Timer(300, e -> reloadViewport());
            viewportTimer.setRepeats(false);
        }
        viewportTimer.restart();
    }

    void reloadViewport() {
        if (viewportLoader == null) return;
        if (hasHomeLocation && distanceToHomeMeters() > MAX_CITY_DISTANCE_METERS) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        double[][] corners = visibleCoords(w, h);
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
        ViewportRequest request = queuedViewportRequest;
        queuedViewportRequest = null;
        viewportLoadRunning = true;
        activeViewportKey = request.key;
        return request;
    }

    private void loadViewport(ViewportRequest request) {
        viewportLoaderExecutor.submit(() -> {
            List<Activity> found = Collections.emptyList();
            try {
                found = viewportLoader.load(request.south, request.west,
                        request.north, request.east, request.maxResults);
            } catch (Exception ignored) {
                // A failed public lookup leaves the current markers intact.
            }
            List<Activity> completed = found;
            SwingUtilities.invokeLater(() -> finishViewportLoad(request, completed));
        });
    }

    private void finishViewportLoad(ViewportRequest completed, List<Activity> found) {
        ViewportRequest next = null;
        boolean apply;
        synchronized (this) {
            apply = queuedViewportRequest == null;
            if (apply) lastLoadedViewportKey = completed.key;
            viewportLoadRunning = false;
            activeViewportKey = "";
            if (queuedViewportRequest != null) next = takeQueuedViewportRequest();
        }
        if (apply && found != null && !found.isEmpty()) mergeViewportResults(found);
        if (next == null) {
            notifyPlacesLoading(false);
        } else {
            loadViewport(next);
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
        if (placesLoadingListener != null) {
            placesLoadingListener.accept(loading);
        }
    }

    private void mergeViewportResults(List<Activity> found) {
        Map<String, Activity> byId = new HashMap<>();
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
        double cx = latLngToPixelX(centerLng);
        double cy = latLngToPixelY(centerLat);
        double westPixel = cx - (w / 2.0);
        double eastPixel = cx + (w / 2.0);
        double northPixel = cy - (h / 2.0);
        double southPixel = cy + (h / 2.0);
        double west = clampLng(pixelXToLng(westPixel));
        double east = clampLng(pixelXToLng(eastPixel));
        double north = pixelYToLat(visiblePixelY(northPixel));
        double south = pixelYToLat(visiblePixelY(southPixel));
        if (south > north) { double t = south; south = north; north = t; }
        if (west > east) { double t = east; east = west; west = t; }
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
        double max = Math.pow(2, zoom) * TILE_SIZE;
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
        double lat1 = Math.toRadians(centerLat);
        double lat2 = Math.toRadians(homeLat);
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(homeLng - centerLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
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
        List<Activity> visible = visibleActivities();
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
        double latSpan = maxLat - minLat;
        double lngSpan = maxLng - minLng;
        zoom = 13;
        int pw = getWidth(), ph = getHeight();
        if (pw <= 0 || ph <= 0) return;
        for (int z = 17; z >= MIN_ZOOM; z--) {
            double metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(centerLat)) / Math.pow(2, z);
            double spanMeters = Math.max(latSpan * 111320.0, lngSpan * 111320.0 * Math.cos(Math.toRadians(centerLat)));
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
        double latRad = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0
                * Math.pow(2, zoom) * TILE_SIZE;
    }

    private double pixelXToLng(double px) {
        return px / (Math.pow(2, zoom) * TILE_SIZE) * 360.0 - 180.0;
    }

    private double pixelYToLat(double py) {
        double n = 1.0 - 2.0 * py / (Math.pow(2, zoom) * TILE_SIZE);
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * n)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        double centerPixelX = latLngToPixelX(centerLng);
        double centerPixelY = latLngToPixelY(centerLat);

        int centerTileX = (int) Math.floor(centerPixelX / TILE_SIZE);
        int centerTileY = (int) Math.floor(centerPixelY / TILE_SIZE);

        int tilesX = (w / TILE_SIZE) + 3;
        int tilesY = (h / TILE_SIZE) + 3;

        int maxTiles = (int) Math.pow(2, zoom);
        int currentZoom = zoom;

        for (int tx = -tilesX / 2; tx <= tilesX / 2; tx++) {
            for (int ty = -tilesY / 2; ty <= tilesY / 2; ty++) {
                int cx = centerTileX + tx;
                int cy = centerTileY + ty;
                if (cx < 0 || cx >= maxTiles || cy < 0 || cy >= maxTiles) continue;
                int px = (int) (w / 2.0 + (cx * TILE_SIZE - centerPixelX));
                int py = (int) (h / 2.0 + (cy * TILE_SIZE - centerPixelY));
                String key = currentZoom + "/" + cx + "/" + cy;
                BufferedImage tile = tileLoadingEnabled ? tileCache.get(key) : null;
                if (tile != null) {
                    g2.drawImage(tile, px, py, null);
                } else {
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

        Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.setFont(SwingTheme.BODY);
        int visibleCount = visibleActivities().size();
        String title = visibleCount + " places in " + city
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
        List<int[]> points = routePoints(w, h, centerPixelX, centerPixelY);
        if (points.size() < 2) {
            return;
        }
        Stroke oldStroke = g2.getStroke();
        g2.setColor(ROUTE_COLOR);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, new float[] {6f, 6f}, 0f));
        for (int i = 1; i < points.size(); i++) {
            int[] from = points.get(i - 1);
            int[] to = points.get(i);
            g2.drawLine(from[0], from[1], to[0], to[1]);
        }
        g2.setStroke(oldStroke);
    }

    /** Screen positions of the scheduled stops, in Day Plan order. */
    private List<int[]> routePoints(int w, int h, double centerPixelX, double centerPixelY) {
        List<int[]> points = new ArrayList<>();
        for (String id : scheduledActivityIds) {
            for (Activity activity : activities) {
                if (!activity.getId().equals(id) || activity.getLocation() == null) {
                    continue;
                }
                double px = latLngToPixelX(activity.getLocation().getLongitude());
                double py = latLngToPixelY(activity.getLocation().getLatitude());
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
        List<String> ordered = new ArrayList<>();
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
            double lng = a.getLocation().getLongitude();
            double lat = a.getLocation().getLatitude();
            double markerPxX = latLngToPixelX(lng);
            double markerPxY = latLngToPixelY(lat);
            int sx = (int) (w / 2.0 + (markerPxX - centerPixelX));
            int sy = (int) (h / 2.0 + (markerPxY - centerPixelY));
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
        List<Activity> ordered = new ArrayList<>(visibleActivities());
        ordered.sort((left, right) -> Integer.compare(
                markerLayer(left.getId()), markerLayer(right.getId())));
        List<Activity> limited = new ArrayList<>();
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
        boolean selected = activityId.equals(selectedActivityId);
        boolean bookmarked = bookmarkedIds.contains(activityId);
        int scheduleIndex = scheduledActivityIds.indexOf(activityId);
        int radius = markerRadius(activityId);

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
            String label = String.valueOf(scheduleIndex + 1);
            g2.setColor(Color.WHITE);
            g2.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
            int textWidth = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, sx - textWidth / 2, sy + 5);
            if (bookmarked) {
                drawBookmarkBadge(g2, sx + radius - 4, sy - radius + 3);
            }
        } else if (bookmarked) {
            drawBookmarkGlyph(g2, sx, sy, radius);
        } else {
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
        int width = Math.max(10, radius - 3);
        int height = Math.max(15, radius + 3);
        int left = sx - width / 2;
        int top = sy - height / 2;
        Polygon ribbon = new Polygon(
                new int[]{left, left + width, left + width, sx, left},
                new int[]{top, top, top + height, top + height - 5, top + height}, 5);
        g2.setColor(Color.WHITE);
        g2.fillPolygon(ribbon);
    }

    private void drawBookmarkBadge(Graphics2D g2, int sx, int sy) {
        g2.setColor(COLOR_BOOKMARKED);
        g2.fillOval(sx - 7, sy - 7, 14, 14);
        g2.setColor(Color.WHITE);
        Polygon ribbon = new Polygon(
                new int[]{sx - 3, sx + 3, sx + 3, sx, sx - 3},
                new int[]{sy - 4, sy - 4, sy + 4, sy + 1, sy + 4}, 5);
        g2.fillPolygon(ribbon);
    }

    String markerText(String activityId) {
        int index = scheduledActivityIds.indexOf(activityId);
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
                String url = "https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png";
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "Trippy-CSC207/1.0")
                        .GET().build();
                HttpResponse<InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    BufferedImage img = ImageIO.read(response.body());
                    if (img != null) {
                        tileCache.put(key, img);
                        SwingUtilities.invokeLater(this::repaint);
                    }
                }
                response.body().close();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            } finally {
                pendingLoads.remove(key);
            }
        });
    }
}
