package closeai;

import closeai.adapters.controllers.ApiController;
import closeai.adapters.views.CloseAIFrame;
import closeai.adapters.views.GalleryPanel;
import closeai.adapters.views.NewItineraryDialog;
import closeai.application.AppContainer;
import closeai.domain.entities.Activity;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import closeai.infrastructure.persistence.CachedPlacesRepository;
import closeai.infrastructure.web.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.Cursor;
import java.awt.Dimension;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--web".equals(args[0])) {
            startWebPrototype();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                AppBuilder builder = new AppBuilder();
                AppContainer app = builder.build();
                seedDemoTrip(app);
                List<Activity> demoActivities = app.activities.findAll();
                Trip torontoTrip = seedTrip(app, demoActivities, "Toronto",
                        LocalDate.of(2026, 7, 23),
                        LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
                Trip nycTrip = seedTrip(app, demoActivities, "New York City",
                        LocalDate.of(2026, 8, 15),
                        LocalTime.of(10, 0), LocalTime.of(20, 0), TransportationMode.DRIVING);
                Trip montrealTrip = seedTrip(app, demoActivities, "Montreal",
                        LocalDate.of(2026, 9, 5),
                        LocalTime.of(8, 0), LocalTime.of(17, 0), TransportationMode.WALKING);

                showGallery(builder, app);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    private static void showGallery(AppBuilder builder, AppContainer app) {
        List<Trip> trips = app.trips.findAll();

        JFrame galleryFrame = new JFrame("CloseAI - My Trips");
        galleryFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        galleryFrame.setMinimumSize(new Dimension(800, 600));
        galleryFrame.setPreferredSize(new Dimension(960, 700));
        galleryFrame.add(new GalleryPanel(trips,
            trip -> openTripFrame(builder, app, trip, galleryFrame),
            () -> {
                NewItineraryDialog dialog = new NewItineraryDialog(galleryFrame);
                dialog.setVisible(true);
                if (!dialog.isConfirmed()) return;
                String dest = dialog.getDestination();
                LocalDate date = dialog.getDate();
                galleryFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                new Thread(() -> {
                    try {
                        Trip created = app.createTrip.execute(dest, date,
                                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
                        SwingUtilities.invokeLater(() -> {
                            CloseAIFrame tripFrame = openTripFrame(builder, app, created, galleryFrame);
                            enrichItineraryAsync(builder, app, created.getId(), dest, tripFrame);
                        });
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        SwingUtilities.invokeLater(() -> {
                            galleryFrame.setCursor(Cursor.getDefaultCursor());
                            JOptionPane.showMessageDialog(galleryFrame,
                                    "Could not create the itinerary: " + exception.getMessage(),
                                    "New Itinerary", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }, "Create-Itinerary").start();
            }
        ));
        galleryFrame.pack();
        galleryFrame.setLocationRelativeTo(null);
        galleryFrame.setVisible(true);
        System.out.println(
                "CloseAI gallery launched on EDT: "
                        + SwingUtilities.isEventDispatchThread());
    }

    private static CloseAIFrame openTripFrame(
            AppBuilder builder, AppContainer app, Trip trip, JFrame galleryFrame) {
        galleryFrame.dispose();
        CloseAIFrame tripFrame = builder.buildFrameForTrip(app, trip);
        tripFrame.setOnHomeAction(() -> {
            tripFrame.dispose();
            showGallery(builder, app);
        });
        tripFrame.setVisible(true);
        return tripFrame;
    }

    private static void startWebPrototype() throws Exception {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.build();
        CachedPlacesRepository cachedPlaces = builder.getCachedPlaces();

        Trip demo = app.createTrip.execute("Toronto", LocalDate.of(2026, 7, 18), LocalTime.of(9, 0),
                LocalTime.of(19, 0), TransportationMode.WALKING);
        for (Activity activity : app.activities.findAll()) {
            if (activity.getId().equals("rom") || activity.getId().equals("pai") || activity.getId().equals("cn-tower"))
                app.bookmarkActivity.execute(demo.getId(), activity.getId());
        }
        app.autoSchedule.execute(demo.getId());
        System.setProperty("closeai.demoTripId", demo.getId());

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api", new ApiController(app, cachedPlaces));
        server.createContext("/", new StaticFileHandler("frontend"));
        server.setExecutor(null);
        server.start();
        System.out.println("CloseAI is running at http://localhost:8080");
        System.out.println("Demo trip id: " + demo.getId());
    }

    private static Trip seedDemoTrip(AppContainer app) {
        Trip created = app.createTrip.execute(
                "Toronto",
                LocalDate.of(2026, 7, 18),
                LocalTime.of(9, 0),
                LocalTime.of(19, 0),
                TransportationMode.WALKING);
        for (Activity activity : app.activities.findAll()) {
            if (activity.getId().equals("rom") || activity.getId().equals("pai") || activity.getId().equals("cn-tower"))
                app.bookmarkActivity.execute(created.getId(), activity.getId());
        }
        app.autoSchedule.execute(created.getId());
        System.setProperty("closeai.demoTripId", created.getId());
        return created;
    }

    /**
     * Populates a freshly created trip with real activities from the destination in the
     * background, then pushes the enriched state into the already-visible trip frame.
     */
    private static void enrichItineraryAsync(AppBuilder builder, AppContainer app,
                                             String tripId, String destination, CloseAIFrame frame) {
        new Thread(() -> {
            try {
                List<Activity> available = app.searchActivities.execute(destination, "");
                CachedPlacesRepository cachedPlaces = builder.getCachedPlaces();
                if (cachedPlaces != null && !available.isEmpty()) {
                    cachedPlaces.clear();
                    cachedPlaces.addAll(available);
                }
                Trip updated = app.trips.findById(tripId).orElse(null);
                if (updated != null) {
                    updated.setDiscoveredPlaces(available);
                    app.trips.save(updated);
                    SwingUtilities.invokeLater(() -> builder.refreshFrameForTrip(updated, frame));
                }
            } catch (Exception exception) {
                System.err.println("[Main] Could not enrich itinerary for " + destination
                        + ": " + exception.getMessage());
            }
        }, "Enrich-Itinerary").start();
    }

    private static Trip seedTrip(AppContainer app, List<Activity> available,
                                  String destination, LocalDate date,
                                  LocalTime start, LocalTime end, TransportationMode mode) {
        Trip created = app.createTrip.execute(destination, date, start, end, mode);
        created.setDiscoveredPlaces(available);
        app.trips.save(created);
        addBookmarksAndPlan(app, created.getId(), available);
        return app.trips.findById(created.getId())
                .orElseThrow(() -> new IllegalStateException("Seeded trip was not saved"));
    }

    private static void addBookmarksAndPlan(AppContainer app, String tripId, List<Activity> available) {
        int bookmarks = Math.min(available.size(), 3);
        for (int i = 0; i < bookmarks; i++) {
            try {
                app.bookmarkActivity.execute(tripId, available.get(i).getId());
            } catch (IllegalArgumentException ignored) {
            }
        }
        LocalTime[] slots = { LocalTime.of(10, 0), LocalTime.of(12, 45), LocalTime.of(15, 0) };
        int added = 0;
        for (Activity activity : available) {
            if (added >= 2) break;
            try {
                app.addActivityToPlan.execute(tripId, activity.getId(), slots[added]);
                added++;
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
