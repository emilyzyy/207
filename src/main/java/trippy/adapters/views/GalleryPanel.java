package trippy.adapters.views;

import trippy.application.ports.DestinationGeocoder;
import trippy.application.search.GeoPoint;
import trippy.domain.entities.Trip;
import trippy.domain.valueobjects.TransportationMode;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public final class GalleryPanel extends JPanel {
    private static final int CARD_ARC = 16;
    private static final Color OVERLAY = new Color(13, 35, 64, 180);
    private static final Color OVERLAY_TOP = new Color(13, 35, 64, 100);
    private static final int MAP_ZOOM = 11;

    private final transient Consumer<Trip> onOpenTrip;
    private final transient Runnable onCreateTrip;
    private final transient Consumer<Trip> onDeleteTrip;
    private final transient DestinationGeocoder destinationGeocoder;
    private final transient Runnable onAuthAction;
    private final JPanel cardGrid;
    private final Map<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    private JButton authButton;

    public GalleryPanel(List<Trip> trips, Consumer<Trip> onOpenTrip, Runnable onCreateTrip) {
        this(trips, onOpenTrip, onCreateTrip, null, null, null, false);
    }

    public GalleryPanel(
            List<Trip> trips,
            Consumer<Trip> onOpenTrip,
            Runnable onCreateTrip,
            Runnable onAuthAction,
            boolean signedIn) {
        this(trips, onOpenTrip, onCreateTrip, null, null, onAuthAction, signedIn);
    }

    public GalleryPanel(
            List<Trip> trips,
            Consumer<Trip> onOpenTrip,
            Runnable onCreateTrip,
            Consumer<Trip> onDeleteTrip,
            DestinationGeocoder destinationGeocoder,
            Runnable onAuthAction,
            boolean signedIn) {
        this.onOpenTrip = onOpenTrip;
        this.onCreateTrip = onCreateTrip;
        this.onDeleteTrip = onDeleteTrip;
        this.destinationGeocoder = destinationGeocoder;
        this.onAuthAction = onAuthAction;
        setLayout(new BorderLayout());
        setBackground(SwingTheme.BACKGROUND);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SwingTheme.BACKGROUND);
        header.setBorder(BorderFactory.createEmptyBorder(24, 40, 8, 40));

        JLabel title = new JLabel("My Trips");
        title.setFont(SwingTheme.TITLE);
        header.add(title, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton newTrip = SwingTheme.primaryButton("+ New Itinerary");
        newTrip.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        newTrip.addActionListener(e -> onCreateTrip.run());
        actions.add(newTrip);
        if (onAuthAction != null) {
            authButton = new JButton(signedIn ? "Sign out" : "Sign in");
            authButton.setFont(SwingTheme.BODY);
            authButton.addActionListener(e -> onAuthAction.run());
            actions.add(authButton);
        }
        header.add(actions, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        cardGrid = new JPanel(new GridLayout(0, 2, 20, 20));
        cardGrid.setBackground(SwingTheme.BACKGROUND);
        cardGrid.setBorder(BorderFactory.createEmptyBorder(8, 40, 40, 40));

        for (Trip trip : trips) {
            cardGrid.add(createCard(trip));
        }

        JScrollPane scroll = new JScrollPane(cardGrid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        startTileLoading(trips);
    }

    private JPanel createCard(Trip trip) {
        return new GalleryCard(trip, onOpenTrip);
    }

    private void startTileLoading(List<Trip> trips) {
        for (Trip trip : trips) {
            String key = trip.getDestination().toLowerCase();
            tileForDestination(trip.getDestination())
                    .thenAccept(img -> {
                        if (img != null) {
                            tileCache.put(key, img);
                            SwingUtilities.invokeLater(this::repaintCards);
                        }
                    });
        }
    }

    private CompletableFuture<BufferedImage> tileForDestination(String destination) {
        if (destinationGeocoder == null) {
            return StaticTileLoader.loadCityTile(destination, MAP_ZOOM);
        }
        return CompletableFuture.supplyAsync(() -> destinationGeocoder.geocode(destination))
                .thenCompose(point -> StaticTileLoader.loadTile(
                        point.getLatitude(), point.getLongitude(), MAP_ZOOM))
                .exceptionally(exception -> null);
    }

    private void repaintCards() {
        for (Component comp : cardGrid.getComponents()) {
            if (comp instanceof GalleryCard) {
                comp.repaint();
            }
        }
    }

    BufferedImage tileFor(String destination) {
        return tileCache.get(destination.toLowerCase());
    }

    private final class GalleryCard extends JPanel {
        private final Trip trip;
        private final transient Consumer<Trip> onOpenTrip;

        GalleryCard(Trip trip, Consumer<Trip> onOpenTrip) {
            this.trip = trip;
            this.onOpenTrip = onOpenTrip;
            setOpaque(false);
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(320, 260));

            JPanel overlay = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setComposite(AlphaComposite.SrcOver.derive(0.55f));
                    g2.setColor(Color.BLACK);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                }
            };
            overlay.setOpaque(false);

            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setOpaque(false);

            JLabel name = new JLabel(trip.getDestination());
            name.setFont(SwingTheme.HEADING.deriveFont(Font.BOLD, 20f));
            name.setForeground(Color.WHITE);
            name.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(name);

            content.add(Box.createVerticalStrut(6));

            String dateStr = trip.getDate().getMonth().name().charAt(0)
                    + trip.getDate().getMonth().name().substring(1).toLowerCase()
                    + " " + trip.getDate().getDayOfMonth() + ", " + trip.getDate().getYear();
            JLabel dateLabel = new JLabel(dateStr);
            dateLabel.setForeground(new Color(200, 215, 235));
            dateLabel.setFont(SwingTheme.BODY);
            dateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(dateLabel);

            content.add(Box.createVerticalStrut(4));

            JLabel meta = new JLabel(trip.getStartTime() + " \u2013 " + trip.getEndTime());
            meta.setForeground(new Color(170, 185, 205));
            meta.setFont(SwingTheme.SMALL);
            meta.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(meta);

            content.add(Box.createVerticalStrut(4));

            JLabel counts = new JLabel(trip.getBookmarkedActivities().size() + " bookmarks  \u00b7  "
                    + trip.getScheduledEvents().size() + " events");
            counts.setForeground(new Color(150, 165, 185));
            counts.setFont(SwingTheme.SMALL);
            counts.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(counts);

            content.add(Box.createVerticalStrut(14));

            JButton openButton = new JButton("Open Trip");
            openButton.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
            openButton.setForeground(Color.WHITE);
            openButton.setBackground(SwingTheme.BLUE);
            openButton.setOpaque(true);
            openButton.setContentAreaFilled(true);
            openButton.setBorderPainted(false);
            openButton.setFocusPainted(false);
            openButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            openButton.setBorder(BorderFactory.createEmptyBorder(10, 28, 10, 28));
            openButton.addActionListener(e -> onOpenTrip.accept(trip));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            buttons.setOpaque(false);
            buttons.setAlignmentX(Component.CENTER_ALIGNMENT);
            buttons.add(openButton);
            if (onDeleteTrip != null) {
                JButton deleteButton = new JButton("Delete Trip");
                deleteButton.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
                deleteButton.setForeground(Color.WHITE);
                deleteButton.setBackground(new Color(180, 45, 45));
                deleteButton.setOpaque(true);
                deleteButton.setContentAreaFilled(true);
                deleteButton.setBorderPainted(false);
                deleteButton.setFocusPainted(false);
                deleteButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
                deleteButton.addActionListener(event -> confirmDelete());
                buttons.add(deleteButton);
            }
            content.add(buttons);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            overlay.add(content, gbc);

            add(overlay, BorderLayout.CENTER);
        }

        private void confirmDelete() {
            int answer = JOptionPane.showConfirmDialog(
                    GalleryPanel.this,
                    "Delete the trip to " + trip.getDestination()
                            + "? This cannot be undone.",
                    "Delete Trip",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            try {
                onDeleteTrip.accept(trip);
            } catch (RuntimeException exception) {
                JOptionPane.showMessageDialog(
                        GalleryPanel.this,
                        "Could not delete the trip: " + exception.getMessage(),
                        "Delete Trip",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth(), h = getHeight();

            BufferedImage tile = tileFor(trip.getDestination());
            if (tile != null) {
                double tileAspect = (double) StaticTileLoader.TILE_SIZE / StaticTileLoader.TILE_SIZE;
                double cardAspect = (double) w / h;
                int drawW, drawH;
                if (tileAspect > cardAspect) {
                    drawH = h;
                    drawW = (int) (h * tileAspect);
                } else {
                    drawW = w;
                    drawH = (int) (w / tileAspect);
                }
                int ox = (w - drawW) / 2;
                int oy = (h - drawH) / 2;
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, CARD_ARC, CARD_ARC));
                g2.drawImage(tile, ox, oy, drawW, drawH, null);
            } else {
                g2.setColor(new Color(30, 50, 80));
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, CARD_ARC, CARD_ARC));
            }

            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, CARD_ARC, CARD_ARC));
            g2.setColor(OVERLAY);
            g2.fillRect(0, 0, w, h);

            g2.setColor(new Color(255, 255, 255, 40));
            g2.draw(new java.awt.geom.RoundRectangle2D.Float(0, 0, w - 1, h - 1, CARD_ARC, CARD_ARC));

            g2.dispose();
        }

        @Override
        protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CARD_ARC, CARD_ARC));
            super.paintChildren(g2);
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 320), Math.max(d.height, 260));
        }
    }
}
