package trippy;

import trippy.adapters.controllers.ApiController;
import trippy.adapters.views.TrippyFrame;
import trippy.adapters.views.GalleryPanel;
import trippy.adapters.views.LoginDialog;
import trippy.adapters.views.NewItineraryDialog;
import trippy.application.AppContainer;
import trippy.application.ports.AuthService;
import trippy.application.ports.DestinationGeocoder;
import trippy.application.ports.AuthSession;
import trippy.application.usecases.CreateTripInputData;
import trippy.domain.entities.Activity;
import trippy.domain.entities.Trip;
import trippy.domain.valueobjects.TransportationMode;
import trippy.infrastructure.config.DotEnv;
import trippy.infrastructure.persistence.DualModeItineraryDataAccess;
import trippy.infrastructure.supabase.SupabaseAuthClient;
import trippy.infrastructure.web.StaticFileHandler;
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
                DotEnv.load();
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                System.setProperty("trippy.places.mode",
                        System.getProperty("trippy.places.mode", "nominatim"));
                System.setProperty("trippy.weather.mode",
                        System.getProperty("trippy.weather.mode", "open-meteo"));
                AppBuilder builder = new AppBuilder();
                boolean supabase = isSupabaseMode();
                if (supabase) {
                    System.setProperty("trippy.persistence.mode", "supabase");
                    AuthService auth = createSupabaseAuth();
                    AppContainer app = builder.build(auth);
                    showGallery(builder, app, auth);
                } else {
                    AppContainer app = builder.build();
                    new Thread(() -> {
                        try {
                            seedTrip(app, "Toronto",
                                    LocalDate.of(2026, 7, 23),
                                    LocalTime.of(9, 0), LocalTime.of(18, 0));
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                        SwingUtilities.invokeLater(() -> showGallery(builder, app, null));
                    }, "Seed-Toronto").start();
                }
            } catch (Exception exception) {
                exception.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        exception.getMessage(),
                        "Trippy failed to start",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Supabase mode when {@code trippy.persistence.mode} is set to "supabase"; otherwise
     * auto-enabled when the Supabase credentials are present, so adding them to .env is enough.
     */
    private static boolean isSupabaseMode() {
        String mode = System.getProperty("trippy.persistence.mode", "");
        if (!mode.isEmpty()) {
            return "supabase".equalsIgnoreCase(mode);
        }
        return DotEnv.supabaseConfigured();
    }

    private static AuthService createSupabaseAuth() {
        String url = DotEnv.get("TRIPPY_SUPABASE_URL", "trippy.supabase.url");
        String anonKey = DotEnv.get("TRIPPY_SUPABASE_ANON_KEY", "trippy.supabase.anonKey");
        if (url == null || anonKey == null) {
            throw new IllegalStateException(
                    "Supabase mode requires TRIPPY_SUPABASE_URL and TRIPPY_SUPABASE_ANON_KEY in .env");
        }
        return new SupabaseAuthClient(url, anonKey);
    }

    private static boolean promptSignIn(AuthService auth, JFrame owner) {
        while (true) {
            LoginDialog dialog = new LoginDialog(owner);
            dialog.setVisible(true);
            if (!dialog.isConfirmed()) {
                return false;
            }
            try {
                AuthSession session = dialog.isSignUp()
                        ? auth.signUp(dialog.getEmail(), dialog.getPassword())
                        : auth.signIn(dialog.getEmail(), dialog.getPassword());
                System.out.println("Signed in as " + session.getEmail());
                return true;
            } catch (RuntimeException exception) {
                JOptionPane.showMessageDialog(owner,
                        exception.getMessage(),
                        "Sign in failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void showGallery(AppBuilder builder, AppContainer app, AuthService auth) {
        List<Trip> trips = app.listTrips.execute();
        boolean signedIn = auth != null && auth.currentSession().isPresent();

        JFrame galleryFrame = new JFrame("Trippy - My Trips");
        galleryFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        galleryFrame.setMinimumSize(new Dimension(800, 600));
        galleryFrame.setPreferredSize(new Dimension(960, 700));

        Runnable onAuth = auth == null ? null : () -> {
            if (auth.currentSession().isPresent()) {
                signOut(app, auth);
                galleryFrame.dispose();
                showGallery(builder, app, auth);
            } else if (promptSignIn(auth, galleryFrame)) {
                galleryFrame.dispose();
                showGallery(builder, app, auth);
            }
        };

        galleryFrame.add(new GalleryPanel(
                trips,
                trip -> openTripFrame(builder, app, trip, galleryFrame, auth),
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
                            LocalTime.of(9, 0), LocalTime.of(18, 0),
                            TransportationMode.WALKING, dialog.getDayCount());
                            SwingUtilities.invokeLater(() -> {
                                TrippyFrame tripFrame =
                                        openTripFrame(builder, app, created, galleryFrame, auth);
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
                },
                trip -> {
                    app.deleteTrip.execute(trip.getId());
                    galleryFrame.dispose();
                    showGallery(builder, app, auth);
                },
                app.places instanceof DestinationGeocoder
                        ? (DestinationGeocoder) app.places : null,
                onAuth,
                signedIn));
        galleryFrame.pack();
        galleryFrame.setLocationRelativeTo(null);
        galleryFrame.setVisible(true);
        System.out.println(
                "Trippy gallery launched on EDT: "
                        + SwingUtilities.isEventDispatchThread());
    }

    private static TrippyFrame openTripFrame(
            AppBuilder builder, AppContainer app, Trip trip, JFrame galleryFrame, AuthService auth) {
        galleryFrame.dispose();
        TrippyFrame tripFrame = builder.buildFrameForTrip(app, trip);
        tripFrame.setOnHomeAction(() -> {
            tripFrame.dispose();
            showGallery(builder, app, auth);
        });
        if (auth != null) {
            tripFrame.setAuthAction(() -> handleTripAuth(builder, app, auth, tripFrame, trip.getId()),
                    auth.currentSession().isPresent());
        }
        tripFrame.setVisible(true);
        return tripFrame;
    }

    private static void handleTripAuth(
            AppBuilder builder, AppContainer app, AuthService auth,
            TrippyFrame tripFrame, String tripId) {
        if (auth.currentSession().isPresent()) {
            signOut(app, auth);
            tripFrame.dispose();
            showGallery(builder, app, auth);
            return;
        }
        if (!promptSignIn(auth, tripFrame)) {
            return;
        }
        try {
            DualModeItineraryDataAccess dual = dualMode(app);
            if (dual != null) {
                dual.syncTripToCloud(tripId);
            } else {
                app.trips.findById(tripId).ifPresent(app.trips::save);
            }
            tripFrame.setAuthAction(
                    () -> handleTripAuth(builder, app, auth, tripFrame, tripId), true);
            JOptionPane.showMessageDialog(tripFrame,
                    "Signed in. This itinerary was saved to your account.",
                    "Signed in",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(tripFrame,
                    "Signed in, but saving failed: " + exception.getMessage(),
                    "Save failed",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void signOut(AppContainer app, AuthService auth) {
        auth.signOut();
        DualModeItineraryDataAccess dual = dualMode(app);
        if (dual != null) {
            dual.clearLocal();
        }
    }

    private static DualModeItineraryDataAccess dualMode(AppContainer app) {
        if (app.trips instanceof DualModeItineraryDataAccess) {
            return (DualModeItineraryDataAccess) app.trips;
        }
        return null;
    }

    private static void startWebPrototype() throws Exception {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.build();

        Trip demo = app.createTrip.execute(new CreateTripInputData(
                "Toronto",
                LocalDate.of(2026, 7, 18),
                LocalTime.of(9, 0),
                LocalTime.of(19, 0),
                TransportationMode.WALKING));
        for (Activity activity : app.activities.findAll()) {
            if (activity.getId().equals("rom") || activity.getId().equals("pai") || activity.getId().equals("cn-tower"))
                app.bookmarkActivity.execute(demo.getId(), activity.getId());
        }
        app.autoSchedule.execute(demo.getId());
        System.setProperty("trippy.demoTripId", demo.getId());

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api", new ApiController(app));
        server.createContext("/", new StaticFileHandler("frontend"));
        server.setExecutor(null);
        server.start();
        System.out.println("Trippy is running at http://localhost:8080");
        System.out.println("Demo trip id: " + demo.getId());
    }

    private static void enrichItineraryAsync(AppBuilder builder, AppContainer app,
                                             String tripId, String destination, TrippyFrame frame) {
        frame.getSearchViewModel().setLoading(true);
        new Thread(() -> {
            try {
                Trip updated = app.discoverTripPlaces.execute(tripId, destination);
                SwingUtilities.invokeLater(() -> builder.refreshFrameForTrip(updated, frame));
            } catch (Exception exception) {
                System.err.println("[Main] Could not enrich itinerary for " + destination
                        + ": " + exception.getMessage());
                SwingUtilities.invokeLater(() -> frame.getSearchViewModel().setLoading(false));
            }
        }, "Enrich-Itinerary").start();
    }

    private static Trip seedTrip(AppContainer app, String destination, LocalDate date,
                                 LocalTime start, LocalTime end) {
        Trip created = app.createTrip.execute(destination, date, start, end, TransportationMode.WALKING);
        List<Activity> places =
                app.discoverTripPlaces.execute(created.getId(), destination).getDiscoveredPlaces();
        DemoSeeding.bookmarkAndSchedule(app, created.getId(), places, 2, 3);
        return app.trips.findById(created.getId())
                .orElseThrow(() -> new IllegalStateException("Seeded trip was not saved"));
    }
}
