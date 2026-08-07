package closeai.adapters.views;

import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

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
    private Point dragStart;
    private boolean isDragging;

    private List<Activity> activities = new ArrayList<>();
    private String city = "the area";
    private Set<String> bookmarkedIds = Collections.emptySet();
    private Set<String> scheduledIds = Collections.emptySet();
    private List<String> scheduledActivityIds = Collections.emptyList();
    private String selectedActivityId = "";
    private boolean showHighlightedOnly = false;

    private final JCheckBox highlightOnly = new JCheckBox("Bookmarks & calendar only");

    private final ConcurrentHashMap<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    private final Set<String> pendingLoads = ConcurrentHashMap.newKeySet();
    private final ExecutorService tileLoader = Executors.newFixedThreadPool(2,
            r -> { Thread t = new Thread(r, "TileLoader"); t.setDaemon(true); return t; });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(8)).build();
    private final boolean tileLoadingEnabled;

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
                dragStart = e.getPoint();
                isDragging = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                isDragging = false;
                setCursor(Cursor.getDefaultCursor());
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
        if (sameIds(newList, this.activities)) return;
        this.activities = newList;
        if (!this.activities.isEmpty()) fitToActivities();
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
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getId().equals(b.get(i).getId())) return false;
        }
        return true;
    }

    public void flyTo(double lat, double lng) {
        centerLat = lat;
        centerLng = lng;
        repaint();
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
            drawMarker(g2, a.getId(), sx, sy);
            g2.setColor(new Color(0x1a, 0x1f, 0x36));
            g2.setFont(SwingTheme.SMALL);
            String name = a.getName();
            if (name.length() > 25) name = name.substring(0, 22) + "...";
            g2.drawString(name, sx + 18, sy + 4);
        }
    }

    private void drawMarker(Graphics2D g2, String activityId, int sx, int sy) {
        boolean selected = activityId.equals(selectedActivityId);
        boolean bookmarked = bookmarkedIds.contains(activityId);
        int scheduleIndex = scheduledActivityIds.indexOf(activityId);
        int radius = selected ? 18 : 14;

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
