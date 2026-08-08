package trippy;

import trippy.adapters.controllers.ActivityDiscoveryController;
import trippy.adapters.controllers.AutoScheduleController;
import trippy.adapters.controllers.BookmarkController;
import trippy.adapters.controllers.ManualPlanController;
import trippy.adapters.controllers.ShareTripController;
import trippy.adapters.controllers.SwingTaskRunner;
import trippy.adapters.controllers.TripDayController;
import trippy.adapters.controllers.TripOptionsController;
import trippy.adapters.controllers.TripAssistantController;
import trippy.adapters.presenters.ActivityDiscoveryPresenter;
import trippy.adapters.presenters.AutoSchedulePresenter;
import trippy.adapters.presenters.ManualPlanPresenter;
import trippy.adapters.presenters.ShareTripPresenter;
import trippy.adapters.presenters.TripAssistantPresenter;
import trippy.adapters.presenters.TripOptionsPresenter;
import trippy.adapters.viewmodels.BookmarksState;
import trippy.adapters.viewmodels.BookmarksViewModel;
import trippy.adapters.viewmodels.ActivitySelectionViewModel;
import trippy.adapters.viewmodels.CalendarViewModel;
import trippy.adapters.viewmodels.DashboardState;
import trippy.adapters.viewmodels.DashboardViewModel;
import trippy.adapters.viewmodels.DayPlanState;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.adapters.viewmodels.SearchState;
import trippy.adapters.viewmodels.SearchViewModel;
import trippy.adapters.viewmodels.ShareState;
import trippy.adapters.viewmodels.ShareViewModel;
import trippy.adapters.viewmodels.TripOptionsState;
import trippy.adapters.viewmodels.TripOptionsViewModel;
import trippy.adapters.viewmodels.TripAssistantState;
import trippy.adapters.viewmodels.TripAssistantViewModel;
import trippy.adapters.views.BookmarksPanel;
import trippy.adapters.views.TrippyFrame;
import trippy.adapters.views.DayPlanPanel;
import trippy.adapters.views.DaySwitcherPanel;
import trippy.adapters.views.HeaderPanel;
import trippy.adapters.views.OverviewPanel;
import trippy.adapters.views.PlannerPanel;
import trippy.adapters.views.SearchPanel;
import trippy.adapters.views.TripAssistantPanel;
import trippy.adapters.views.TripOptionsDialog;
import trippy.adapters.gateways.DistanceServiceTravelTimeEstimator;
import trippy.adapters.gateways.WeatherServiceContextGateway;
import trippy.application.AppContainer;
import trippy.application.PlaceHydrator;
import trippy.application.autoschedule.AutoScheduleInteractor;
import trippy.application.autoschedule.engine.ScheduleEngine;
import trippy.application.autoschedule.policy.DaylightPolicy;
import trippy.application.autoschedule.policy.MealWindowPolicy;
import trippy.application.autoschedule.policy.SoftPolicy;
import trippy.application.autoschedule.policy.WeatherSuitabilityPolicy;
import trippy.application.ports.AuthService;
import trippy.application.ports.PlacesService;
import trippy.application.ports.DestinationGeocoder;
import trippy.application.ports.TripRepository;
import trippy.application.ports.TripAssistantGateway;
import trippy.application.ports.WeatherService;
import trippy.application.scheduling.DefaultActivityScoringPolicy;
import trippy.application.usecases.CreateTripInputData;
import trippy.application.tripassistant.TripAssistantDecision;
import trippy.application.tripassistant.TripAssistantInteractor;
import trippy.application.tripassistant.TripAssistantMessage;
import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.Trip;
import trippy.domain.entities.WeatherWarning;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import trippy.domain.valueobjects.WeatherSeverity;
import trippy.infrastructure.config.DotEnv;
import trippy.infrastructure.ai.FallbackTripAssistantGateway;
import trippy.infrastructure.ai.OfflineTripAssistantGateway;
import trippy.infrastructure.ai.OpenAiTripAssistantGateway;
import trippy.infrastructure.mock.MockPlacesService;
import trippy.infrastructure.mock.MockWeatherService;
import trippy.infrastructure.places.CachingPlacesService;
import trippy.infrastructure.places.OpenStreetMapPlacesService;
import trippy.infrastructure.persistence.CachedPlacesRepository;
import trippy.infrastructure.persistence.DualModeItineraryDataAccess;
import trippy.infrastructure.persistence.DayScopedTripRepository;
import trippy.infrastructure.persistence.InMemoryItineraryDataAccessObject;
import trippy.infrastructure.routing.FastestModeDistanceService;
import trippy.infrastructure.routing.OsrmDistanceService;
import trippy.infrastructure.supabase.SupabaseItineraryDataAccess;
import trippy.infrastructure.weather.OpenMeteoWeatherService;
import java.net.URI;
import java.time.Duration;
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
    private static final String DEFAULT_OPENAI_MODEL = "gpt-5.4-mini";
    private static final String DEFAULT_PROXY_ENDPOINT =
            "https://trippy-george-proxy.power-feast.workers.dev/v1/responses";

    public AppContainer build() {
        return build(null);
    }

    /** Builds the app; pass a signed-in {@link AuthService} when using Supabase persistence. */
    public AppContainer build(AuthService authSession) {
        String weatherMode = System.getProperty("trippy.weather.mode", "mock");
        String placesMode = System.getProperty("trippy.places.mode", "mock");
        WeatherService weather = "open-meteo".equalsIgnoreCase(weatherMode)
                ? new OpenMeteoWeatherService() : new MockWeatherService();
        return buildWithServices(
                weather, "nominatim".equalsIgnoreCase(placesMode), authSession);
    }

    public AppContainer buildOffline() {
        return buildWithServices(new MockWeatherService(), false, null);
    }

    public AppContainer buildLive() {
        return buildWithServices(new OpenMeteoWeatherService(), false, null);
    }

    /** Builds Swing with a seeded demo trip so the interactive map is populated on launch. */
    public TrippyFrame buildSwingApplication() {
        return buildSwingApplication(build(), true);
    }

    /**
     * Builds a Swing frame for a specific trip, used by the gallery flow.
     *
     * <p>The frame opens immediately with a "loading" weather placeholder; the live forecast is
     * fetched on a background thread and pushed into the dashboard when it arrives, so the EDT
     * never blocks on the network.</p>
     */
    public TrippyFrame buildFrameForTrip(AppContainer app, Trip trip) {
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
        ActivitySelectionViewModel activitySelectionViewModel =
                new ActivitySelectionViewModel();
        DayPlanViewModel dayPlanViewModel = new DayPlanViewModel(
                new DayPlanState(
                        trip.getId(),
                        trip.getScheduledEvents(),
                        "Seeded demo. Choose Autoschedule to arrange this day.",
                        false,
                        Collections.emptyList(),
                        trip.getTripDates(),
                        trip.getActiveDayIndex()));
        TripOptionsViewModel tripOptionsViewModel = new TripOptionsViewModel(
                TripOptionsState.fromTrip(trip, "", false));
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
        ManualPlanPresenter manualPlanPresenter = new ManualPlanPresenter(
                dayPlanViewModel, searchViewModel);
        ManualPlanController manualPlanController = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                () -> dayPlanViewModel.getState().getTripId(), manualPlanPresenter);
        TripDayController tripDayController = new TripDayController(
                app.trips, () -> dayPlanViewModel.getState().getTripId(),
                manualPlanPresenter);

        HeaderPanel headerPanel = new HeaderPanel(
                dashboardViewModel, dayPlanViewModel, shareController);
        OverviewPanel overviewPanel = new OverviewPanel(
                dashboardViewModel, searchViewModel, bookmarksViewModel,
                dayPlanViewModel, activitySelectionViewModel);
        overviewPanel.setViewportPlacesLoader(
                (south, west, north, east, maxResults) ->
                        app.places.searchInBounds(south, west, north, east, maxResults));
        if (app.places instanceof DestinationGeocoder) {
            overviewPanel.setDestinationGeocoder((DestinationGeocoder) app.places);
        }
        SearchPanel searchPanel = new SearchPanel(
                searchViewModel, discoveryController, bookmarkController,
                manualPlanController, activitySelectionViewModel,
                dayPlanViewModel, tripOptionsViewModel);
        BookmarksPanel bookmarksPanel = new BookmarksPanel(
                bookmarksViewModel, bookmarkController,
                manualPlanController, activitySelectionViewModel,
                dayPlanViewModel, tripOptionsViewModel);
        DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, autoScheduleController,
                        manualPlanController, activitySelectionViewModel,
                        tripDayController);
        dayPlanPanel.setTripDefaults(trip.getStartTime(), trip.getEndTime());
        TripOptionsPresenter tripOptionsPresenter = new TripOptionsPresenter(
                dashboardViewModel, dayPlanViewModel, tripOptionsViewModel);
        TripOptionsController tripOptionsController = new TripOptionsController(
                app.editItinerary,
                () -> app.trips.findById(dayPlanViewModel.getState().getTripId())
                        .orElse(null),
                tripOptionsPresenter);
        dayPlanPanel.setOpenOptionsAction(() -> new TripOptionsDialog(
                dayPlanPanel, tripOptionsViewModel, tripOptionsController).showDialog());
        tripOptionsViewModel.addPropertyChangeListener(event -> {
            TripOptionsState options = tripOptionsViewModel.getState();
            dayPlanPanel.setTripDefaults(options.getStartTime(), options.getEndTime());
        });
        TripAssistantPanel tripAssistantPanel = buildTripAssistant(
                app, dayPlanViewModel,
                "Hi, I'm George. Ask me what to visit, what works in rain, or what fits your day.");
        DaySwitcherPanel daySwitcherPanel =
                new DaySwitcherPanel(dayPlanViewModel, tripDayController);
        PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel,
                daySwitcherPanel);
        TrippyFrame frame = new TrippyFrame(
                headerPanel,
                overviewPanel,
                plannerPanel,
                dayPlanPanel,
                tripAssistantPanel,
                dayPlanViewModel,
                calendarViewModel,
                shareViewModel,
                searchViewModel,
                bookmarksViewModel);
        refreshWeatherAsync(app, trip, dashboardViewModel, dayPlanViewModel);
        return frame;
    }

    /** Builds Swing around an injected application container for deterministic integration tests. */
    public TrippyFrame buildSwingApplication(AppContainer app) {
        return buildSwingApplication(app, false);
    }

    private TrippyFrame buildSwingApplication(AppContainer app, boolean seedDemo) {
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
        ActivitySelectionViewModel activitySelectionViewModel =
                new ActivitySelectionViewModel();
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
        ManualPlanPresenter manualPlanPresenter = new ManualPlanPresenter(
                dayPlanViewModel, searchViewModel);
        ManualPlanController manualPlanController = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                () -> dayPlanViewModel.getState().getTripId(), manualPlanPresenter);
        TripDayController tripDayController = new TripDayController(
                app.trips, () -> dayPlanViewModel.getState().getTripId(),
                manualPlanPresenter);

        HeaderPanel headerPanel = new HeaderPanel(
                dashboardViewModel, dayPlanViewModel, shareController);
        OverviewPanel overviewPanel = new OverviewPanel(
                dashboardViewModel, searchViewModel, bookmarksViewModel,
                dayPlanViewModel, activitySelectionViewModel);
        overviewPanel.setViewportPlacesLoader(
                (south, west, north, east, maxResults) ->
                        app.places.searchInBounds(south, west, north, east, maxResults));
        if (app.places instanceof DestinationGeocoder) {
            overviewPanel.setDestinationGeocoder((DestinationGeocoder) app.places);
        }
        SearchPanel searchPanel = new SearchPanel(
                searchViewModel, discoveryController, bookmarkController,
                manualPlanController, activitySelectionViewModel,
                dayPlanViewModel, tripOptionsViewModel);
        BookmarksPanel bookmarksPanel = new BookmarksPanel(
                bookmarksViewModel, bookmarkController,
                manualPlanController, activitySelectionViewModel,
                dayPlanViewModel, tripOptionsViewModel);
        DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, autoScheduleController,
                        manualPlanController, activitySelectionViewModel,
                        tripDayController);
        TripAssistantPanel tripAssistantPanel = buildTripAssistant(
                app, dayPlanViewModel,
                "Hi, I'm George. Create a trip, then ask me for activity recommendations.");
        DaySwitcherPanel daySwitcherPanel =
                new DaySwitcherPanel(dayPlanViewModel, tripDayController);
        PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel,
                daySwitcherPanel);
        TrippyFrame frame = new TrippyFrame(
                headerPanel,
                overviewPanel,
                plannerPanel,
                dayPlanPanel,
                tripAssistantPanel,
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
            dashboardViewModel.setState(new DashboardState(
                    demo.getDestination(), demo.getDate(), "Loading weather…",
                    "Weather and places refresh in the background."));
            tripOptionsViewModel.setState(TripOptionsState.fromTrip(demo, "", false));
            refreshFrameForTrip(demo, frame);
            refreshWeatherAsync(app, demo, dashboardViewModel, dayPlanViewModel);
        }
        return frame;
    }

    private TripAssistantPanel buildTripAssistant(
            AppContainer app, DayPlanViewModel dayPlanViewModel, String greeting) {
        TripAssistantViewModel viewModel = new TripAssistantViewModel(
                new TripAssistantState(Collections.singletonList(new TripAssistantMessage(
                        TripAssistantMessage.Role.ASSISTANT, greeting)), false, ""));
        TripAssistantPresenter presenter = new TripAssistantPresenter(viewModel);
        TripAssistantInteractor interactor = new TripAssistantInteractor(
                app.trips, app.activities, app.weather, tripAssistantGateway(), presenter);
        TripAssistantController controller = new TripAssistantController(
                interactor, () -> dayPlanViewModel.getState().getTripId(),
                presenter, viewModel, new SwingTaskRunner());
        return new TripAssistantPanel(viewModel, controller);
    }

    private TripAssistantGateway tripAssistantGateway() {
        TripAssistantGateway offline = new OfflineTripAssistantGateway();
        String mode = System.getProperty("trippy.chatbot.mode", "proxy");
        if ("offline".equalsIgnoreCase(mode)) {
            return offline;
        }
        String model = DotEnv.get("OPENAI_MODEL", "trippy.openai.model");
        if (model == null) {
            model = DEFAULT_OPENAI_MODEL;
        }
        if ("proxy".equalsIgnoreCase(mode)) {
            return proxyTripAssistantGateway(offline, model);
        }
        if (!"openai".equalsIgnoreCase(mode)) {
            return offline;
        }
        String apiKey = DotEnv.get("OPENAI_API_KEY", "openai.api.key");
        if (apiKey == null) {
            return request -> {
                TripAssistantDecision decision = offline.answer(request);
                return new TripAssistantDecision(
                        decision.getIntent(), decision.getActivityIds(),
                        decision.getAnswer(),
                        "OPENAI_API_KEY is not configured, so George used offline mode.",
                        decision.getRequestedFact());
            };
        }
        return new FallbackTripAssistantGateway(
                new OpenAiTripAssistantGateway(apiKey, model), offline);
    }

    private TripAssistantGateway proxyTripAssistantGateway(
            TripAssistantGateway offline, String model) {
        String endpoint = DotEnv.get("TRIPPY_AI_PROXY_URL", "trippy.ai.proxy.url");
        if (endpoint == null) {
            endpoint = DEFAULT_PROXY_ENDPOINT;
        }
        if (endpoint.trim().isEmpty()) {
            return request -> {
                TripAssistantDecision decision = offline.answer(request);
                return new TripAssistantDecision(
                        decision.getIntent(), decision.getActivityIds(),
                        decision.getAnswer(),
                        "George's live service is not configured, so offline mode was used.",
                        decision.getRequestedFact());
            };
        }
        try {
            return new FallbackTripAssistantGateway(
                    OpenAiTripAssistantGateway.viaProxy(URI.create(endpoint), model), offline);
        } catch (IllegalArgumentException exception) {
            return offline;
        }
    }

    /**
     * Pushes a refreshed snapshot of the trip into an already-built frame. Used by the gallery
     * flow to populate a newly created trip after its real activities finish loading.
     */
    public void refreshFrameForTrip(Trip trip, TrippyFrame frame) {
        frame.getSearchViewModel().setState(searchStateFor(trip));
        frame.getBookmarksViewModel().setState(new BookmarksState(trip.getBookmarkedActivities()));
        DayPlanState current = frame.getDayPlanViewModel().getState();
        frame.getDayPlanViewModel().setState(new DayPlanState(
                trip.getId(),
                trip.getScheduledEvents(),
                "Seeded demo. Choose Autoschedule to arrange this day.",
                false,
                current.getHourlyWeather(),
                trip.getTripDates(),
                trip.getActiveDayIndex()));
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
                new DayScopedTripRepository(app.trips,
                        () -> dayPlanViewModel.getState().getActiveDayIndex()),
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
                                     DashboardViewModel dashboardViewModel,
                                     DayPlanViewModel dayPlanViewModel) {
        Thread worker = new Thread(() -> {
            List<WeatherWarning> hourlyWeather = weatherWarningsFor(app, trip);
            WeatherWarning result = closestToTripStart(hourlyWeather, trip);
            DashboardState state = new DashboardState(
                    trip.getDestination(),
                    trip.getDate(),
                    result.getWeatherCondition(),
                    result.getMessage());
            SwingUtilities.invokeLater(() -> {
                dashboardViewModel.setState(state);
                DayPlanState current = dayPlanViewModel.getState();
                dayPlanViewModel.setState(new DayPlanState(
                        current.getTripId(), current.getEvents(), current.getMessage(),
                        current.isError(), hourlyWeather, current.getTripDates(),
                        current.getActiveDayIndex()));
            });
        }, "Weather-" + trip.getDestination());
        worker.setDaemon(true);
        worker.start();
    }

    /** Fetches the weather preview, degrading gracefully so network or date errors cannot crash the UI. */
    private List<WeatherWarning> weatherWarningsFor(AppContainer app, Trip trip) {
        try {
            return app.weatherWarning.executeHourly(trip.getId());
        } catch (Exception exception) {
            System.err.println("[AppBuilder] Weather preview unavailable for " + trip.getDestination()
                    + ": " + exception.getMessage());
            return Collections.singletonList(new WeatherWarning(
                    new Location(0, 0, trip.getDestination()),
                    trip.getStartTime(),
                    "Weather preview unavailable",
                    WeatherSeverity.LOW,
                    "Could not fetch a forecast for " + trip.getDestination()
                            + ". The trip date may be outside the forecast range, or you may be offline."));
        }
    }

    private WeatherWarning closestToTripStart(
            List<WeatherWarning> hourlyWeather, Trip trip) {
        WeatherWarning closest = null;
        long closestMinutes = Long.MAX_VALUE;
        for (WeatherWarning warning : hourlyWeather) {
            if (warning == null || warning.getTime() == null) continue;
            long difference = Math.abs(Duration.between(
                    trip.getStartTime(), warning.getTime()).toMinutes());
            if (difference < closestMinutes) {
                closest = warning;
                closestMinutes = difference;
            }
        }
        return closest == null ? placeholderWarning(trip) : closest;
    }

    private AppContainer buildWithServices(
            WeatherService weather, boolean useLivePlaces, AuthService authSession) {
        MockPlacesService mockPlaces = new MockPlacesService();
        CachedPlacesRepository cachedPlaces = new CachedPlacesRepository();
        cachedPlaces.addAll(mockPlaces.findAll());
        PlacesService places = useLivePlaces
                ? new CachingPlacesService(
                        new OpenStreetMapPlacesService(), cachedPlaces)
                : mockPlaces;

        String persistence = System.getProperty("trippy.persistence.mode", "memory");
        TripRepository trips;
        if ("supabase".equalsIgnoreCase(persistence)) {
            AuthService auth = authSession;
            if (auth == null) {
                throw new IllegalStateException(
                        "Supabase persistence requires an AuthService instance");
            }
            String url = DotEnv.get("TRIPPY_SUPABASE_URL", "trippy.supabase.url");
            String anonKey = DotEnv.get("TRIPPY_SUPABASE_ANON_KEY", "trippy.supabase.anonKey");
            if (url == null || anonKey == null) {
                throw new IllegalStateException(
                        "Set TRIPPY_SUPABASE_URL and TRIPPY_SUPABASE_ANON_KEY in .env");
            }
            PlaceHydrator hydrator = new PlaceHydrator(cachedPlaces, places, cachedPlaces);
            InMemoryItineraryDataAccessObject local = new InMemoryItineraryDataAccessObject();
            SupabaseItineraryDataAccess remote =
                    new SupabaseItineraryDataAccess(url, anonKey, auth, hydrator);
            trips = new DualModeItineraryDataAccess(local, remote, auth);
        } else {
            trips = new InMemoryItineraryDataAccessObject();
        }

        return new AppContainer(
                trips,
                places,
                cachedPlaces,
                new FastestModeDistanceService(new OsrmDistanceService()),
                weather,
                new DefaultActivityScoringPolicy(),
                (trippy.application.ports.ItineraryDataAccessInterface) trips);
    }
}
