package closeai;

import closeai.adapters.controllers.ActivityDiscoveryController;
import closeai.adapters.controllers.AutoScheduleController;
import closeai.adapters.controllers.BookmarkController;
import closeai.adapters.controllers.ShareTripController;
import closeai.adapters.controllers.SwingTaskRunner;
import closeai.adapters.controllers.TripSetupController;
import closeai.adapters.presenters.ActivityDiscoveryPresenter;
import closeai.adapters.presenters.AutoSchedulePresenter;
import closeai.adapters.presenters.ShareTripPresenter;
import closeai.adapters.presenters.TripSetupPresenter;
import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.CalendarViewModel;
import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.adapters.viewmodels.ShareState;
import closeai.adapters.viewmodels.ShareViewModel;
import closeai.adapters.viewmodels.TripOptionsState;
import closeai.adapters.viewmodels.TripOptionsViewModel;
import closeai.adapters.views.BookmarksPanel;
import closeai.adapters.views.CloseAIFrame;
import closeai.adapters.views.DayPlanPanel;
import closeai.adapters.views.HeaderPanel;
import closeai.adapters.views.OverviewPanel;
import closeai.adapters.views.PlannerPanel;
import closeai.adapters.views.SearchPanel;
import closeai.adapters.views.TripOptionsPanel;
import closeai.adapters.gateways.DistanceServiceTravelTimeEstimator;
import closeai.adapters.gateways.WeatherServiceContextGateway;
import closeai.application.AppContainer;
import closeai.application.autoschedule.AutoScheduleInteractor;
import closeai.application.autoschedule.engine.ScheduleEngine;
import closeai.application.autoschedule.policy.DaylightPolicy;
import closeai.application.autoschedule.policy.MealWindowPolicy;
import closeai.application.autoschedule.policy.SoftPolicy;
import closeai.application.autoschedule.policy.WeatherSuitabilityPolicy;
import closeai.application.ports.PlacesService;
import closeai.application.ports.WeatherService;
import closeai.application.scheduling.DefaultActivityScoringPolicy;
import closeai.application.usecases.CreateTripInputData;
import closeai.application.usecases.TripSetupOutputData;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import closeai.domain.valueobjects.WeatherSeverity;
import closeai.infrastructure.mock.MockPlacesService;
import closeai.infrastructure.mock.MockWeatherService;
import closeai.infrastructure.places.CachingPlacesService;
import closeai.infrastructure.places.NominatimPlacesService;
import closeai.infrastructure.persistence.CachedPlacesRepository;
import closeai.infrastructure.persistence.InMemoryItineraryDataAccessObject;
import closeai.infrastructure.routing.OsrmDistanceService;
import closeai.infrastructure.weather.OpenMeteoWeatherService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;

/** Outer composition root for selecting infrastructure without leaking it into application code. */
public final class AppBuilder {
    public AppContainer build() {
        String weatherMode = System.getProperty("closeai.weather.mode", "mock");
        String placesMode = System.getProperty("closeai.places.mode", "mock");
        WeatherService weather = "open-meteo".equalsIgnoreCase(weatherMode)
                ? new OpenMeteoWeatherService() : new MockWeatherService();
        return buildWithServices(
                weather, "nominatim".equalsIgnoreCase(placesMode));
    }

    public AppContainer buildOffline() {
        return buildWithServices(new MockWeatherService(), false);
    }

    public AppContainer buildLive() {
        return buildWithServices(new OpenMeteoWeatherService(), false);
    }

    /** Builds Swing with a seeded demo trip so the interactive map is populated on launch. */
    public CloseAIFrame buildSwingApplication() {
        return buildSwingApplication(build(), true);
    }

    /**
     * Builds a Swing frame for a specific trip, used by the gallery flow.
     *
     * <p>The frame opens immediately with a "loading" weather placeholder; the live forecast is
     * fetched on a background thread and pushed into the dashboard when it arrives, so the EDT
     * never blocks on the network.</p>
     */
    public CloseAIFrame buildFrameForTrip(AppContainer app, Trip trip) {
        WeatherWarning warning = placeholderWarning(trip);

        DashboardViewModel dashboardViewModel = new DashboardViewModel(
                new DashboardState(
                        trip.getDestination(),
                        trip.getDate(),
                        warning.getWeatherCondition(),
                        warning.getMessage()));
        SearchViewModel searchViewModel = new SearchViewModel(searchStateFor(trip));
        BookmarksViewModel bookmarksViewModel = new BookmarksViewModel(
                new BookmarksState(trip.getBookmarkedActivities()));
        DayPlanViewModel dayPlanViewModel = new DayPlanViewModel(
                new DayPlanState(
                        trip.getId(),
                        trip.getScheduledEvents(),
                        "Seeded demo. Choose Autoschedule to arrange this day.",
                        false));
        TripOptionsViewModel tripOptionsViewModel = new TripOptionsViewModel(
                new TripOptionsState(
                        trip.getDestination(),
                        trip.getDate(),
                        trip.getStartTime(),
                        trip.getEndTime()));
        CalendarViewModel calendarViewModel = new CalendarViewModel(
                dashboardViewModel, dayPlanViewModel);
        ShareViewModel shareViewModel = new ShareViewModel(
                new ShareState("", "", false));

        AutoScheduleController autoScheduleController =
                buildAutoSchedule(app, dayPlanViewModel);
        ShareTripPresenter sharePresenter = new ShareTripPresenter(shareViewModel);
        ShareTripController shareController = new ShareTripController(
                app.share,
                () -> dayPlanViewModel.getState().getTripId(),
                sharePresenter);
        ActivityDiscoveryPresenter discoveryPresenter = new ActivityDiscoveryPresenter(
                searchViewModel, bookmarksViewModel);
        ActivityDiscoveryController discoveryController = new ActivityDiscoveryController(
                app.searchActivities, app.filterActivities,
                () -> dashboardViewModel.getState().getDestination(), discoveryPresenter);
        BookmarkController bookmarkController = new BookmarkController(
                app.bookmarkActivity, app.removeBookmark,
                () -> dayPlanViewModel.getState().getTripId(), searchViewModel, discoveryPresenter);

        HeaderPanel headerPanel = new HeaderPanel(
                dashboardViewModel, dayPlanViewModel, shareController);
        OverviewPanel overviewPanel = new OverviewPanel(dashboardViewModel, searchViewModel);
        SearchPanel searchPanel = new SearchPanel(
                searchViewModel, discoveryController, bookmarkController);
        BookmarksPanel bookmarksPanel = new BookmarksPanel(
                bookmarksViewModel, bookmarkController);
        DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, autoScheduleController);
        dayPlanPanel.setTripDefaults(trip.getStartTime(), trip.getEndTime(),
                trip.getTransportationMode());
        TripOptionsPanel tripOptionsPanel =
                new TripOptionsPanel(tripOptionsViewModel);
        PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel, tripOptionsPanel);
        CloseAIFrame frame = new CloseAIFrame(
                headerPanel,
                overviewPanel,
                plannerPanel,
                dayPlanPanel,
                dayPlanViewModel,
                calendarViewModel,
                shareViewModel,
                searchViewModel,
                bookmarksViewModel);
        refreshWeatherAsync(app, trip, dashboardViewModel);
        return frame;
    }

    /** Builds Swing around an injected application container for deterministic integration tests. */
    public CloseAIFrame buildSwingApplication(AppContainer app) {
        return buildSwingApplication(app, false);
    }

    private CloseAIFrame buildSwingApplication(AppContainer app, boolean seedDemo) {
        if (app == null) {
            throw new IllegalArgumentException("Application container is required");
        }
        DashboardViewModel dashboardViewModel = new DashboardViewModel(
                new DashboardState(
                        "", null, "Offline ready",
                        "Create a trip to load weather for its destination."));
        SearchViewModel searchViewModel = new SearchViewModel(
                new SearchState(app.activities.findAll(), ""));
        BookmarksViewModel bookmarksViewModel = new BookmarksViewModel(
                new BookmarksState(Collections.emptyList()));
        DayPlanViewModel dayPlanViewModel = new DayPlanViewModel(
                new DayPlanState(
                        "", Collections.emptyList(),
                        "Create a trip before planning or autoscheduling.", false));
        CalendarViewModel calendarViewModel = new CalendarViewModel(
                dashboardViewModel, dayPlanViewModel);
        ShareViewModel shareViewModel = new ShareViewModel(
                new ShareState("", "Create a trip before sharing.", false));
        TripOptionsViewModel tripOptionsViewModel = new TripOptionsViewModel(
                new TripOptionsState(
                        "",
                        LocalDate.now().plusDays(1),
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)));

        TripSetupPresenter tripSetupPresenter = new TripSetupPresenter(
                dashboardViewModel,
                searchViewModel,
                bookmarksViewModel,
                dayPlanViewModel,
                tripOptionsViewModel,
                app.weatherWarning,
                app.searchActivities);
        TripSetupController tripSetupController = new TripSetupController(
                app.createTrip,
                app.editItinerary,
                () -> tripOptionsViewModel.getState().getTripId(),
                tripSetupPresenter);

        AutoScheduleController autoScheduleController =
                buildAutoSchedule(app, dayPlanViewModel);
        ShareTripPresenter sharePresenter = new ShareTripPresenter(shareViewModel);
        ShareTripController shareController = new ShareTripController(
                app.share,
                () -> dayPlanViewModel.getState().getTripId(),
                sharePresenter);
        ActivityDiscoveryPresenter discoveryPresenter = new ActivityDiscoveryPresenter(
                searchViewModel, bookmarksViewModel);
        ActivityDiscoveryController discoveryController = new ActivityDiscoveryController(
                app.searchActivities, app.filterActivities,
                () -> dashboardViewModel.getState().getDestination(), discoveryPresenter);
        BookmarkController bookmarkController = new BookmarkController(
                app.bookmarkActivity, app.removeBookmark,
                () -> dayPlanViewModel.getState().getTripId(), searchViewModel, discoveryPresenter);

        HeaderPanel headerPanel = new HeaderPanel(
                dashboardViewModel, dayPlanViewModel, shareController);
        OverviewPanel overviewPanel = new OverviewPanel(dashboardViewModel, searchViewModel);
        SearchPanel searchPanel = new SearchPanel(
                searchViewModel, discoveryController, bookmarkController);
        BookmarksPanel bookmarksPanel = new BookmarksPanel(
                bookmarksViewModel, bookmarkController);
        DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, autoScheduleController);
        TripOptionsPanel tripOptionsPanel =
                new TripOptionsPanel(tripOptionsViewModel, tripSetupController);
        PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel, tripOptionsPanel);
        CloseAIFrame frame = new CloseAIFrame(
                headerPanel,
                overviewPanel,
                plannerPanel,
                dayPlanPanel,
                dayPlanViewModel,
                calendarViewModel,
                shareViewModel,
                searchViewModel,
                bookmarksViewModel);
        if (seedDemo) {
            Trip demo = app.createTrip.execute(new CreateTripInputData(
                    "Toronto",
                    LocalDate.now().plusDays(5),
                    LocalTime.of(9, 0),
                    LocalTime.of(18, 0),
                    TransportationMode.WALKING));
            tripSetupPresenter.presentSuccess(new TripSetupOutputData(demo, true));
        }
        return frame;
    }

    /**
     * Pushes a refreshed snapshot of the trip into an already-built frame. Used by the gallery
     * flow to populate a newly created trip after its real activities finish loading.
     */
    public void refreshFrameForTrip(Trip trip, CloseAIFrame frame) {
        frame.getSearchViewModel().setState(searchStateFor(trip));
        frame.getBookmarksViewModel().setState(new BookmarksState(trip.getBookmarkedActivities()));
        frame.getDayPlanViewModel().setState(new DayPlanState(
                trip.getId(),
                trip.getScheduledEvents(),
                "Seeded demo. Choose Autoschedule to arrange this day.",
                false));
    }

    /**
     * Assembles the one production Autoschedule path.
     *
     * <p>Everything the use case depends on is chosen here and nowhere else: the engine,
     * the built-in policies, and the gateways that adapt the team's routing and weather
     * services to the contracts scheduling actually needs. The use case itself has never
     * heard of OSRM, TomTom or Open-Meteo.</p>
     */
    private AutoScheduleController buildAutoSchedule(AppContainer app,
                                                     DayPlanViewModel dayPlanViewModel) {
        AutoSchedulePresenter presenter = new AutoSchedulePresenter(dayPlanViewModel);
        List<SoftPolicy> builtInPolicies = Arrays.asList(
                new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(
                app.trips,
                new DistanceServiceTravelTimeEstimator(app.distances),
                new WeatherServiceContextGateway(app.weather),
                presenter,
                builtInPolicies,
                new ScheduleEngine());
        return new AutoScheduleController(interactor, dayPlanViewModel, new SwingTaskRunner());
    }

    /** Search and map view for a trip: every discovered place, tagged with its bookmark/schedule status. */
    private SearchState searchStateFor(Trip trip) {
        Set<String> bookmarkedIds = new HashSet<>();
        for (Activity activity : trip.getBookmarkedActivities()) {
            bookmarkedIds.add(activity.getId());
        }
        Set<String> scheduledIds = new HashSet<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getActivity() != null) {
                scheduledIds.add(event.getActivity().getId());
            }
        }
        return new SearchState(trip.getDiscoveredPlaces(), "", bookmarkedIds, scheduledIds);
    }

    private WeatherWarning placeholderWarning(Trip trip) {
        return new WeatherWarning(
                new Location(0, 0, trip.getDestination()),
                trip.getStartTime(),
                "Loading weather\u2026",
                WeatherSeverity.LOW,
                "Fetching the forecast for " + trip.getDestination() + "\u2026");
    }

    private void refreshWeatherAsync(AppContainer app, Trip trip,
                                     DashboardViewModel dashboardViewModel) {
        Thread worker = new Thread(() -> {
            WeatherWarning result = weatherWarningFor(app, trip);
            DashboardState state = new DashboardState(
                    trip.getDestination(),
                    trip.getDate(),
                    result.getWeatherCondition(),
                    result.getMessage());
            SwingUtilities.invokeLater(() -> dashboardViewModel.setState(state));
        }, "Weather-" + trip.getDestination());
        worker.setDaemon(true);
        worker.start();
    }

    /** Fetches the weather preview, degrading gracefully so network or date errors cannot crash the UI. */
    private WeatherWarning weatherWarningFor(AppContainer app, Trip trip) {
        try {
            return app.weatherWarning.execute(trip.getId());
        } catch (Exception exception) {
            System.err.println("[AppBuilder] Weather preview unavailable for " + trip.getDestination()
                    + ": " + exception.getMessage());
            return new WeatherWarning(
                    new Location(0, 0, trip.getDestination()),
                    trip.getStartTime(),
                    "Weather preview unavailable",
                    WeatherSeverity.LOW,
                    "Could not fetch a forecast for " + trip.getDestination()
                            + ". The trip date may be outside the forecast range, or you may be offline.");
        }
    }

    private AppContainer buildWithServices(
            WeatherService weather, boolean useLivePlaces) {
        InMemoryItineraryDataAccessObject itineraries = new InMemoryItineraryDataAccessObject();
        MockPlacesService mockPlaces = new MockPlacesService();
        CachedPlacesRepository cachedPlaces = new CachedPlacesRepository();
        cachedPlaces.addAll(mockPlaces.findAll());
        PlacesService places = useLivePlaces
                ? new CachingPlacesService(
                        new NominatimPlacesService(), cachedPlaces)
                : mockPlaces;
        return new AppContainer(
                itineraries,
                places,
                cachedPlaces,
                new OsrmDistanceService(),
                weather,
                new DefaultActivityScoringPolicy(),
                itineraries);
    }
}
