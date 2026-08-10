package app;

import java.awt.Cursor;
import java.awt.Dimension;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import app.config.DotEnv;
import com.sun.net.httpserver.HttpServer;
import database.persistence.DualModeItineraryDataAccess;
import database.supabase.SupabaseAuthClient;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.User;
import entity.valueobjects.TransportationMode;
import interface_adapter.controllers.ApiController;
import interface_adapter.controllers.FriendsController;
import interface_adapter.controllers.ProfileController;
import interface_adapter.presenters.FriendsPresenter;
import interface_adapter.presenters.ProfilePresenter;
import interface_adapter.viewmodels.FriendsState;
import interface_adapter.viewmodels.FriendsViewModel;
import interface_adapter.viewmodels.ProfileState;
import interface_adapter.viewmodels.ProfileViewModel;
import interface_adapter.web.StaticFileHandler;
import use_case.ports.AccountService;
import use_case.ports.AuthService;
import use_case.ports.AuthSession;
import use_case.ports.DestinationGeocoder;
import use_case.usecases.CreateTripInputData;
import use_case.usecases.ManageFriendsInteractor;
import use_case.usecases.ManageProfileInteractor;
import views.FriendsDialog;
import views.GalleryPanel;
import views.LoginDialog;
import views.NewItineraryDialog;
import views.ProfileDialog;
import views.TrippyFrame;

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
                final AppBuilder builder = new AppBuilder();
                final boolean supabase = isSupabaseMode();
                if (supabase) {
                    System.setProperty("trippy.persistence.mode", "supabase");
                    final AuthService auth = createSupabaseAuth();
                    final AppContainer app = builder.build(auth);
                    showGallery(builder, app, auth);
                }
                else {
                    final AppContainer app = builder.build();
                    new Thread(() -> {
                        try {
                            seedTrip(app, "Toronto",
                                    LocalDate.of(2026, 7, 23),
                                    LocalTime.of(9, 0), LocalTime.of(18, 0));
                        }
                        catch (Exception exception) {
                            exception.printStackTrace();
                        }
                        SwingUtilities.invokeLater(() -> showGallery(builder, app, null));
                    }, "Seed-Toronto").start();
                }
            }
            catch (Exception exception) {
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
        final String mode = System.getProperty("trippy.persistence.mode", "");
        if (!mode.isEmpty()) {
            return "supabase".equalsIgnoreCase(mode);
        }
        return DotEnv.supabaseConfigured();
    }

    private static AuthService createSupabaseAuth() {
        final String url = DotEnv.get("TRIPPY_SUPABASE_URL", "trippy.supabase.url");
        final String anonKey = DotEnv.get("TRIPPY_SUPABASE_ANON_KEY", "trippy.supabase.anonKey");
        if (url == null || anonKey == null) {
            throw new IllegalStateException(
                    "Supabase mode requires TRIPPY_SUPABASE_URL and TRIPPY_SUPABASE_ANON_KEY in .env");
        }
        return new SupabaseAuthClient(url, anonKey);
    }

    private static boolean promptSignIn(AuthService auth, AccountService account, JFrame owner) {
        while (true) {
            final LoginDialog dialog = new LoginDialog(owner);
            dialog.setVisible(true);
            if (!dialog.isConfirmed()) {
                return false;
            }
            try {
                final AuthSession session = dialog.isSignUp()
                        ? auth.signUp(dialog.getEmail(), dialog.getPassword())
                        : auth.signIn(dialog.getEmail(), dialog.getPassword());
                if (account != null) {
                    final String preferred = dialog.isSignUp() ? dialog.getUsername() : null;
                    final User profile = account.ensureProfile(preferred);
                    System.out.println("Signed in as @" + profile.getUsername()
                            + " (" + session.getEmail() + ")");
                }
                else {
                    System.out.println("Signed in as " + session.getEmail());
                }
                return true;
            }
            catch (RuntimeException exception) {
                JOptionPane.showMessageDialog(owner,
                        exception.getMessage(),
                        "Sign in failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void showGallery(AppBuilder builder, AppContainer app, AuthService auth) {
        final List<Trip> trips = app.listTrips.execute();
        final boolean signedIn = auth != null && auth.currentSession().isPresent();
        final User profile = signedIn ? loadProfile(app) : null;
        final int incomingRequests = signedIn ? countIncomingFriendRequests(app) : 0;
        final Map<String, List<String>> companions = loadTripCompanions(app, trips);

        final JFrame galleryFrame = new JFrame("Trippy - My Trips");
        galleryFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        galleryFrame.setMinimumSize(new Dimension(800, 600));
        galleryFrame.setPreferredSize(new Dimension(960, 700));

        final GalleryPanel[] galleryHolder = new GalleryPanel[1];
        final Runnable onAuth = auth == null ? null : () -> {
            if (promptSignIn(auth, app.account, galleryFrame)) {
                galleryFrame.dispose();
                showGallery(builder, app, auth);
            }
        };
        final Runnable onProfile = !signedIn ? null : () -> {
            final User updated = openProfile(app, auth, galleryFrame);
            if (auth.currentSession().isEmpty()) {
                galleryFrame.dispose();
                showGallery(builder, app, auth);
            }
            else if (updated != null && galleryHolder[0] != null) {
                galleryHolder[0].setProfileUser(updated);
            }
            else if (galleryHolder[0] != null) {
                galleryHolder[0].setProfileUser(loadProfile(app));
            }
        };
        final Runnable onFriends = !signedIn || app.account == null ? null : () -> {
            openFriends(app, galleryFrame);
            if (galleryHolder[0] != null) {
                galleryHolder[0].setIncomingFriendRequestCount(countIncomingFriendRequests(app));
            }
        };

        galleryHolder[0] = new GalleryPanel(
                trips,
                trip -> {
                    final TrippyFrame tripFrame =
                            openTripFrame(builder, app, trip, galleryFrame, auth);
                    enrichItineraryAsync(builder, app, trip.getId(),
                            trip.getDestination(), tripFrame);
                },
                () -> {
                    final List<User> friends = loadFriends(app);
                    final NewItineraryDialog dialog =
                            new NewItineraryDialog(galleryFrame, app.citySearch, friends);
                    dialog.setVisible(true);
                    if (!dialog.isConfirmed()) {
                        return;
                    }
                    final String dest = dialog.getDestination();
                    final LocalDate date = dialog.getDate();
                    final List<String> memberIds = new ArrayList<>();
                    for (User friend : dialog.getSelectedFriends()) {
                        memberIds.add(friend.getId());
                    }
                    app.createTripPresenter.setOnCreated(created -> SwingUtilities.invokeLater(() -> {
                        final TrippyFrame tripFrame =
                                openTripFrame(builder, app, created, galleryFrame, auth);
                        enrichItineraryAsync(builder, app, created.getId(), dest, tripFrame);
                    }));
                    app.createTripPresenter.setOnError(message -> SwingUtilities.invokeLater(() -> {
                        galleryFrame.setCursor(Cursor.getDefaultCursor());
                        JOptionPane.showMessageDialog(galleryFrame,
                                "Could not create the itinerary: " + message,
                                "New Itinerary", JOptionPane.ERROR_MESSAGE);
                    }));
                    galleryFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    new Thread(() -> {
                        try {
                            app.createTripController.create(dest, date,
                                    LocalTime.of(9, 0), LocalTime.of(18, 0),
                                    TransportationMode.WALKING, dialog.getDayCount(), memberIds);
                        }
                        catch (RuntimeException exception) {
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
                onProfile,
                onFriends,
                profile,
                signedIn,
                incomingRequests,
                companions);
        galleryFrame.add(galleryHolder[0]);
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
        final TrippyFrame tripFrame = builder.buildFrameForTrip(app, trip);
        tripFrame.setOnHomeAction(() -> {
            tripFrame.dispose();
            showGallery(builder, app, auth);
        });
        if (auth != null) {
            final boolean signedIn = auth.currentSession().isPresent();
            tripFrame.setAuthAction(() -> handleTripAuth(builder, app, auth, tripFrame, trip.getId()),
                    signedIn);
            tripFrame.setProfileAction(() -> {
                final User updated = openProfile(app, auth, tripFrame);
                if (auth.currentSession().isEmpty()) {
                    tripFrame.dispose();
                    showGallery(builder, app, auth);
                }
                else if (updated != null) {
                    tripFrame.setProfileUser(updated);
                }
                else {
                    tripFrame.setProfileUser(loadProfile(app));
                }
            });
            wireTripFriends(app, tripFrame);
            tripFrame.setProfileUser(loadProfile(app));
            tripFrame.setIncomingFriendRequestCount(countIncomingFriendRequests(app));
            startSharedTripSync(builder, app, tripFrame, trip.getId());
        }
        tripFrame.setVisible(true);
        return tripFrame;
    }

    private static void handleTripAuth(
            AppBuilder builder, AppContainer app, AuthService auth,
            TrippyFrame tripFrame, String tripId) {
        if (auth.currentSession().isPresent()) {
            return;
        }
        if (!promptSignIn(auth, app.account, tripFrame)) {
            return;
        }
        try {
            final DualModeItineraryDataAccess dual = dualMode(app);
            if (dual != null) {
                dual.syncTripToCloud(tripId);
            }
            else {
                app.trips.findById(tripId).ifPresent(app.trips::save);
            }
            tripFrame.setAuthAction(
                    () -> handleTripAuth(builder, app, auth, tripFrame, tripId), true);
            tripFrame.setProfileAction(() -> {
                final User updated = openProfile(app, auth, tripFrame);
                if (auth.currentSession().isEmpty()) {
                    tripFrame.dispose();
                    showGallery(builder, app, auth);
                }
                else if (updated != null) {
                    tripFrame.setProfileUser(updated);
                }
                else {
                    tripFrame.setProfileUser(loadProfile(app));
                }
            });
            wireTripFriends(app, tripFrame);
            tripFrame.setProfileUser(loadProfile(app));
            tripFrame.setIncomingFriendRequestCount(countIncomingFriendRequests(app));
            JOptionPane.showMessageDialog(tripFrame,
                    "Signed in. This itinerary was saved to your account.",
                    "Signed in",
                    JOptionPane.INFORMATION_MESSAGE);
        }
        catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(tripFrame,
                    "Signed in, but saving failed: " + exception.getMessage(),
                    "Save failed",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void wireTripFriends(AppContainer app, TrippyFrame tripFrame) {
        tripFrame.setFriendsAction(() -> {
            if (app.account == null) {
                return;
            }
            openFriends(app, tripFrame);
            tripFrame.setIncomingFriendRequestCount(countIncomingFriendRequests(app));
        });
    }

    private static void openFriends(AppContainer app, JFrame owner) {
        final FriendsViewModel viewModel = new FriendsViewModel(FriendsState.empty());
        final FriendsController controller = new FriendsController(
                new ManageFriendsInteractor(app.account, new FriendsPresenter(viewModel)));
        new FriendsDialog(owner, controller, viewModel).setVisible(true);
    }

    /**
     * Opens the profile editor.
     * @return the saved profile when Save succeeds; {@code null} if cancelled or signed out
     */
    private static User openProfile(AppContainer app, AuthService auth, JFrame owner) {
        if (app.account == null || auth == null) {
            return null;
        }
        final ProfileViewModel viewModel = new ProfileViewModel(ProfileState.empty());
        final ProfileController controller = new ProfileController(
                new ManageProfileInteractor(app.account, auth, new ProfilePresenter(viewModel)));
        controller.load();
        if (viewModel.getState().isError() || viewModel.getState().getProfile() == null) {
            JOptionPane.showMessageDialog(owner,
                    viewModel.getState().getMessage().isEmpty()
                            ? "Could not load profile."
                            : viewModel.getState().getMessage(),
                    "Profile",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        try {
            final ProfileDialog dialog = new ProfileDialog(
                    owner,
                    controller,
                    viewModel,
                    auth.currentSession().map(AuthSession::getPassword).orElse(""));
            dialog.setVisible(true);
            if (dialog.isSignOutRequested()) {
                final DualModeItineraryDataAccess dual = dualMode(app);
                if (dual != null) {
                    dual.clearLocal();
                }
                return null;
            }
            return dialog.isSaved() ? dialog.getSavedProfile() : null;
        }
        catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(owner,
                    exception.getMessage(),
                    "Profile",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private static User loadProfile(AppContainer app) {
        if (app.account == null) {
            return null;
        }
        try {
            return app.account.currentProfile().orElseGet(() -> app.account.ensureProfile(null));
        }
        catch (RuntimeException exception) {
            System.err.println("[Main] Could not load profile: " + exception.getMessage());
            return null;
        }
    }

    private static int countIncomingFriendRequests(AppContainer app) {
        if (app.account == null) {
            return 0;
        }
        try {
            return app.account.listIncomingRequests().size();
        }
        catch (RuntimeException exception) {
            System.err.println("[Main] Could not load friend requests: " + exception.getMessage());
            return 0;
        }
    }

    private static List<User> loadFriends(AppContainer app) {
        if (app.account == null) {
            return java.util.Collections.emptyList();
        }
        try {
            return app.account.listFriends();
        }
        catch (RuntimeException exception) {
            System.err.println("[Main] Could not load friends: " + exception.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private static Map<String, List<String>> loadTripCompanions(AppContainer app, List<Trip> trips) {
        final Map<String, List<String>> companions = new HashMap<>();
        if (app.account == null || trips == null) {
            return companions;
        }
        for (Trip trip : trips) {
            try {
                final List<String> names = app.account.listTripCompanionUsernames(trip.getId());
                if (!names.isEmpty()) {
                    companions.put(trip.getId(), names);
                }
            }
            catch (RuntimeException exception) {
                System.err.println("[Main] Could not load companions for trip "
                        + trip.getId() + ": " + exception.getMessage());
            }
        }
        return companions;
    }

    /**
     * Periodically reloads a shared trip from the cloud so collaborator edits appear live.
     */
    private static void startSharedTripSync(
            AppBuilder builder, AppContainer app, TrippyFrame tripFrame, String tripId) {
        final String[] fingerprint = {""};
        app.trips.findById(tripId).ifPresent(loaded -> fingerprint[0] = tripFingerprint(loaded));

        final javax.swing.Timer syncTimer = new javax.swing.Timer(8000, event -> {
            if (!tripFrame.isDisplayable()) {
                ((javax.swing.Timer) event.getSource()).stop();
                return;
            }
            new Thread(() -> {
                try {
                    final java.util.Optional<Trip> fresh = app.trips.findById(tripId);
                    if (!fresh.isPresent()) {
                        return;
                    }
                    final String next = tripFingerprint(fresh.get());
                    if (!next.equals(fingerprint[0])) {
                        fingerprint[0] = next;
                        SwingUtilities.invokeLater(() ->
                                builder.refreshFrameForTrip(fresh.get(), tripFrame));
                    }
                }
                catch (RuntimeException exception) {
                    System.err.println("[Main] Shared trip sync failed: " + exception.getMessage());
                }
            }, "Shared-Trip-Sync").start();
        });
        syncTimer.setInitialDelay(8000);
        syncTimer.start();
        tripFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                syncTimer.stop();
            }
        });
    }

    private static String tripFingerprint(Trip trip) {
        if (trip == null) {
            return "";
        }
        final StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(trip.getDestination()).append('|')
                .append(trip.getDate()).append('|')
                .append(trip.getStartTime()).append('|')
                .append(trip.getEndTime()).append('|')
                .append(trip.getTransportationMode()).append('|')
                .append(trip.getBookmarkedActivities().size()).append('|')
                .append(trip.getScheduledEvents().size());
        for (Activity activity : trip.getBookmarkedActivities()) {
            fingerprint.append('#').append(activity.getId());
        }
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            fingerprint.append('#').append(event.getId())
                    .append('@').append(event.getStartTime())
                    .append('-').append(event.getEndTime());
        }
        return fingerprint.toString();
    }

    private static void signOut(AppContainer app, AuthService auth) {
        auth.signOut();
        final DualModeItineraryDataAccess dual = dualMode(app);
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
        final AppBuilder builder = new AppBuilder();
        final AppContainer app = builder.build();

        final Trip demo = app.createTrip.executeAndReturn(new CreateTripInputData(
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

        final HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
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
                final Trip updated = app.discoverTripPlaces.execute(tripId, destination);
                SwingUtilities.invokeLater(() -> builder.refreshFrameForTrip(updated, frame));
            }
            catch (Exception exception) {
                System.err.println("[Main] Could not enrich itinerary for " + destination
                        + ": " + exception.getMessage());
                SwingUtilities.invokeLater(() -> frame.getSearchViewModel().setLoading(false));
            }
        }, "Enrich-Itinerary").start();
    }

    private static Trip seedTrip(AppContainer app, String destination, LocalDate date,
                                 LocalTime start, LocalTime end) {
        final Trip created = app.createTrip.execute(destination, date, start, end, TransportationMode.WALKING);
        final List<Activity> places =
                app.discoverTripPlaces.execute(created.getId(), destination).getDiscoveredPlaces();
        DemoSeeding.bookmarkAndSchedule(app, created.getId(), places, 2, 3);
        return app.trips.findById(created.getId())
                .orElseThrow(() -> new IllegalStateException("Seeded trip was not saved"));
    }
}
