package closeai.adapters.views;

import closeai.domain.entities.Activity;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
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

    private List<Activity> activities = new ArrayList<>();
    private String city = "the area";
    private Set<String> bookmarkedIds = Collections.emptySet();
    private Set<String> scheduledIds = Collections.emptySet();
    private boolean showHighlightedOnly = false;

    private final List<Integer> markerHitboxesX = new ArrayList<>();
    private final List<Integer> markerHitboxesY = new ArrayList<>();
    private final List<String> markerIds = new ArrayList<>();
    private java.util.function.Consumer<String> placeSelectionListener;
    private java.util.function.Consumer<List<Activity>> placesLoadedListener;
    private java.util.function.Consumer<Boolean> placesLoadingListener;

    private final JCheckBox highlightOnly = new JCheckBox("Bookmarks & calendar only");

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
                        System.getProperty("closeai.map.tiles.mode", "osm")));
    }

    MapPanel(int width, int height, boolean tileLoadingEnabled) {
        this.tileLoadingEnabled = tileLoadingEnabled;
        setLayout(null);
        setPreferredSize(new Dimension(width, height));
        setOpaque(true);
        setBackground(new Color(232, 239, 244));
        setBorder(BorderFactory.createLineBorder(new Color(180, 200, 215)));
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

    private List<Activity> visibleActivities() {
        if (!showHighlightedOnly) return activities;
        List<Activity> visible = new ArrayList<>();
        for (Activity activity : activities) {
            if (isHighlighted(activity.getId())) visible.add(activity);
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
        repaint();
    }

    /** Sets the map viewport's place loader, enabling live load-as-you-navigate. */
    public void setViewportLoader(ViewportPlacesLoader loader) {
        this.viewportLoader = loader;
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
        for (int i = 0; i < markerHitboxesX.size(); i++) {
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
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        double[][] corners = visibleCoords(w, h);
        final int maxResults = maxResultsForZoom(zoom);
        final double south = corners[0][0];
        final double west = corners[0][1];
        final double north = corners[1][0];
        final double east = corners[1][1];
        viewportLoaderExecutor.submit(() -> {
            try {
                List<Activity> found = viewportLoader.load(south, west, north, east, maxResults);
                SwingUtilities.invokeLater(() -> {
                    mergeViewportResults(found);
                    notifyPlacesLoading(false);
                });
            } catch (Exception ignored) {
                SwingUtilities.invokeLater(() -> notifyPlacesLoading(false));
            }
        });
        notifyPlacesLoading(true);
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
        return Math.max(10, Math.min(300, 6 + z * 3));
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
                SwingUtilities.invokeLater(() -> flyTo(coords[0], coords[1]));
            }
        });
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

        drawMarkers(g2, w, h, centerPixelX, centerPixelY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    private void drawMarkers(Graphics2D g2, int w, int h,
                             double centerPixelX, double centerPixelY) {
        markerHitboxesX.clear();
        markerHitboxesY.clear();
        markerIds.clear();
        for (Activity a : visibleActivities()) {
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
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillOval(sx - 13, sy - 9, 28, 28);
            g2.setColor(markerColor(a.getId()));
            g2.fillOval(sx - 14, sy - 14, 28, 28);
            g2.setColor(new Color(0x1a, 0x1f, 0x36));
            g2.setFont(SwingTheme.SMALL);
            String name = a.getName();
            if (name.length() > 25) name = name.substring(0, 22) + "...";
            g2.drawString(name, sx + 18, sy + 4);
        }
    }

    private void loadTile(int x, int y, int z, String key) {
        if (tileCache.containsKey(key) || !pendingLoads.add(key)) return;
        tileLoader.submit(() -> {
            try {
                String url = "https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png";
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "CloseAI-CSC207/1.0")
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
