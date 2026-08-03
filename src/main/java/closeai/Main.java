package closeai;

import closeai.adapters.controllers.ApiController;
import closeai.adapters.views.CloseAIFrame;
import closeai.adapters.views.GalleryPanel;
import closeai.application.AppContainer;
import closeai.domain.entities.Activity;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import closeai.infrastructure.persistence.CachedPlacesRepository;
import closeai.infrastructure.web.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;
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
                Trip torontoTrip = seedTrip(app, "Toronto", LocalDate.of(2026, 7, 23),
                        LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
                Trip nycTrip = seedTrip(app, "New York City", LocalDate.of(2026, 8, 15),
                        LocalTime.of(10, 0), LocalTime.of(20, 0), TransportationMode.DRIVING);
                Trip montrealTrip = seedTrip(app, "Montreal", LocalDate.of(2026, 9, 5),
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
                String dest = JOptionPane.showInputDialog(galleryFrame,
                        "Enter destination city:", "New Itinerary",
                        JOptionPane.PLAIN_MESSAGE);
                if (dest == null || dest.trim().isEmpty()) return;
                Trip t = seedTrip(app, dest.trim(),
                        LocalDate.now().plusDays(7),
                        LocalTime.of(9, 0), LocalTime.of(18, 0),
                        TransportationMode.WALKING);
                openTripFrame(builder, app, t, galleryFrame);
            }
        ));
        galleryFrame.pack();
        galleryFrame.setLocationRelativeTo(null);
        galleryFrame.setVisible(true);
        System.out.println(
                "CloseAI gallery launched on EDT: "
                        + SwingUtilities.isEventDispatchThread());
    }

    private static void openTripFrame(
            AppBuilder builder, AppContainer app, Trip trip, JFrame galleryFrame) {
        galleryFrame.dispose();
        CloseAIFrame tripFrame = builder.buildFrameForTrip(app, trip);
        tripFrame.setOnHomeAction(() -> {
            tripFrame.dispose();
            showGallery(builder, app);
        });
        tripFrame.setVisible(true);
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

    private static Trip seedTrip(AppContainer app, String destination, LocalDate date,
                                  LocalTime start, LocalTime end, TransportationMode mode) {
        Trip created = app.createTrip.execute(destination, date, start, end, mode);
        List<Activity> available = app.activities.findAll();
        int max = Math.min(available.size(), 3);
        for (int i = 0; i < max; i++) {
            try {
                app.bookmarkActivity.execute(created.getId(), available.get(i).getId());
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<Activity> allActivities = app.activities.findAll();
        LocalTime[] slots = { LocalTime.of(10, 0), LocalTime.of(12, 45), LocalTime.of(15, 0) };
        int added = 0;
        for (Activity activity : allActivities) {
            if (added >= 2) break;
            try {
                app.addActivityToPlan.execute(created.getId(), activity.getId(), slots[added]);
                added++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return app.trips.findById(created.getId())
                .orElseThrow(() -> new IllegalStateException("Seeded trip was not saved"));
    }
}
