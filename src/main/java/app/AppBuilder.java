package app;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;

import app.config.DotEnv;
import database.persistence.CachedPlacesRepository;
import database.persistence.DayScopedTripRepository;
import database.persistence.DualModeItineraryDataAccess;
import database.persistence.InMemoryItineraryDataAccessObject;
import database.supabase.SupabaseAccountClient;
import database.supabase.SupabaseItineraryDataAccess;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.TripAccessLevel;
import entity.valueobjects.TripAssistantMessage;
import entity.valueobjects.WeatherSeverity;
import interface_adapter.ai.FallbackTripAssistantGateway;
import interface_adapter.ai.OfflineTripAssistantGateway;
import interface_adapter.ai.OpenAiTripAssistantGateway;
import interface_adapter.controllers.ActivityDiscoveryController;
import interface_adapter.controllers.AutoScheduleController;
import interface_adapter.controllers.BookmarkController;
import interface_adapter.controllers.ManualPlanController;
import interface_adapter.controllers.ShareTripController;
import interface_adapter.controllers.SwingTaskRunner;
import interface_adapter.controllers.TripAssistantController;
import interface_adapter.controllers.TripDayController;
import interface_adapter.controllers.TripOptionsController;
import interface_adapter.gateways.DistanceServiceTravelTimeEstimator;
import interface_adapter.gateways.WeatherServiceContextGateway;
import interface_adapter.mock.MockPlacesService;
import interface_adapter.mock.MockWeatherService;
import interface_adapter.places.CachingPlacesService;
import interface_adapter.places.OpenStreetMapPlacesService;
import interface_adapter.presenters.ActivityDiscoveryPresenter;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.presenters.ManualPlanPresenter;
import interface_adapter.presenters.ShareTripPresenter;
import interface_adapter.presenters.TripAssistantPresenter;
import interface_adapter.presenters.TripOptionsPresenter;
import interface_adapter.routing.FastestModeDistanceService;
import interface_adapter.routing.OsrmDistanceService;
import interface_adapter.viewmodels.ActivitySelectionViewModel;
import interface_adapter.viewmodels.BookmarksState;
import interface_adapter.viewmodels.BookmarksViewModel;
import interface_adapter.viewmodels.CalendarViewModel;
import interface_adapter.viewmodels.DashboardState;
import interface_adapter.viewmodels.DashboardViewModel;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import interface_adapter.viewmodels.ShareState;
import interface_adapter.viewmodels.ShareViewModel;
import interface_adapter.viewmodels.TripAccessViewModel;
import interface_adapter.viewmodels.TripAssistantState;
import interface_adapter.viewmodels.TripAssistantViewModel;
import interface_adapter.viewmodels.TripOptionsState;
import interface_adapter.viewmodels.TripOptionsViewModel;
import interface_adapter.weather.OpenMeteoWeatherService;
import use_case.autoschedule.AutoScheduleInteractor;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import use_case.ports.AccountService;
import use_case.ports.AuthService;
import use_case.ports.DestinationGeocoder;
import use_case.ports.PlacesService;
import use_case.ports.TripAssistantGateway;
import use_case.ports.TripRepository;
import use_case.ports.WeatherService;
import use_case.scheduling.DefaultActivityScoringPolicy;
import use_case.tripassistant.TripAssistantDecision;
import use_case.tripassistant.TripAssistantInteractor;
import use_case.usecases.CreateTripInputData;
import use_case.usecases.LoadViewportPlacesUseCase;
import use_case.usecases.PlaceHydrator;
import use_case.usecases.ShareTripUseCase;
import views.BookmarksPanel;
import views.DayPlanPanel;
import views.DaySwitcherPanel;
import views.HeaderPanel;
import views.OverviewPanel;
import views.PlannerPanel;
import views.SearchPanel;
import views.TripAssistantPanel;
import views.TripOptionsDialog;
import views.TrippyFrame;

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
        final String weatherMode = System.getProperty("trippy.weather.mode", "mock");
        final String placesMode = System.getProperty("trippy.places.mode", "mock");
        final WeatherService weather = "open-meteo".equalsIgnoreCase(weatherMode)
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
        final WeatherWarning warning = placeholderWarning(trip);

        final DashboardViewModel dashboardViewModel = new DashboardViewModel(
                new DashboardState(
                        trip.getDestination(),
                        trip.getDate(),
                        warning.getWeatherCondition(),
                        warning.getMessage()));
        final SearchViewModel searchViewModel = new SearchViewModel(searchStateFor(trip));
        final BookmarksViewModel bookmarksViewModel = new BookmarksViewModel(
                new BookmarksState(trip.getBookmarkedActivities()));
        final ActivitySelectionViewModel activitySelectionViewModel =
                new ActivitySelectionViewModel();
        final DayPlanViewModel dayPlanViewModel = new DayPlanViewModel(
                new DayPlanState(
                        trip.getId(),
                        trip.getScheduledEvents(),
                        "Seeded demo. Choose Autoschedule to arrange this day.",
                        false,
                        Collections.emptyList(),
                        trip.getTripDates(),
                        trip.getActiveDayIndex()));
        final TripOptionsViewModel tripOptionsViewModel = new TripOptionsViewModel(
                TripOptionsState.fromTrip(trip, "", false));
        final TripAccessViewModel tripAccessViewModel = new TripAccessViewModel();
        final CalendarViewModel calendarViewModel = new CalendarViewModel(
                dashboardViewModel, dayPlanViewModel);
        final ShareViewModel shareViewModel = new ShareViewModel(
                new ShareState("", "", false));

        final AutoScheduleController autoScheduleController =
                buildAutoSchedule(app, dayPlanViewModel);
        final ShareTripPresenter sharePresenter = new ShareTripPresenter(shareViewModel);
        final ShareTripUseCase shareUseCase = new ShareTripUseCase(
                app.summary, app.trips, sharePresenter);
        final ShareTripController shareController = new ShareTripController(
                shareUseCase,
                () -> dayPlanViewModel.getState().getTripId());
        final ActivityDiscoveryPresenter discoveryPresenter = new ActivityDiscoveryPresenter(
                searchViewModel, bookmarksViewModel);
        final ActivityDiscoveryController discoveryController = new ActivityDiscoveryController(
                app.searchActivities, app.filterActivities,
                () -> dashboardViewModel.getState().getDestination(), discoveryPresenter);
        final BookmarkController bookmarkController = new BookmarkController(
                app.bookmarkActivity, app.removeBookmark,
                () -> dayPlanViewModel.getState().getTripId(), searchViewModel, discoveryPresenter);
        final ManualPlanPresenter manualPlanPresenter = new ManualPlanPresenter(
                dayPlanViewModel, searchViewModel);
        final ManualPlanController manualPlanController = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                () -> dayPlanViewModel.getState().getTripId(), manualPlanPresenter);
        final TripDayController tripDayController = new TripDayController(
                app.trips, () -> dayPlanViewModel.getState().getTripId(),
                manualPlanPresenter);

        final HeaderPanel headerPanel = new HeaderPanel(
                dashboardViewModel, dayPlanViewModel, shareController);
        final OverviewPanel overviewPanel = new OverviewPanel(
                dashboardViewModel, searchViewModel, bookmarksViewModel,
                dayPlanViewModel, activitySelectionViewModel);
        overviewPanel.setViewportPlacesLoader(
                new LoadViewportPlacesUseCase(app.places, trip.getDestination()));
        if (app.places instanceof DestinationGeocoder) {
            overviewPanel.setDestinationGeocoder((DestinationGeocoder) app.places);
        }
        final SearchPanel searchPanel = new SearchPanel(
                searchViewModel, discoveryController, bookmarkController,
                manualPlanController, activitySelectionViewModel,
                dayPlanViewModel, tripOptionsViewModel, tripAccessViewModel);
        final BookmarksPanel bookmarksPanel = new BookmarksPanel(
                bookmarksViewModel, bookmarkController,
                manualPlanController, activitySelectionViewModel,
                dayPlanViewModel, tripOptionsViewModel, tripAccessViewModel);
        final DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, autoScheduleController,
                        manualPlanController, activitySelectionViewModel,
                        tripAccessViewModel);
        dayPlanPanel.setTripDefaults(trip.getStartTime(), trip.getEndTime());
        final TripOptionsPresenter tripOptionsPresenter = new TripOptionsPresenter(
                dashboardViewModel, dayPlanViewModel, tripOptionsViewModel);
        final TripOptionsController tripOptionsController = new TripOptionsController(
                app.editItinerary,
                () -> app.trips.findById(dayPlanViewModel.getState().getTripId())
                        .orElse(null),
                tripOptionsPresenter);
        dayPlanPanel.setOpenOptionsAction(() -> new TripOptionsDialog(
                dayPlanPanel, tripOptionsViewModel, tripOptionsController,
                app.account, tripAccessViewModel).showDialog());
        tripOptionsViewModel.addPropertyChangeListener(event -> {
            final TripOptionsState options = tripOptionsViewModel.getState();
            dayPlanPanel.setTripDefaults(options.getStartTime(), options.getEndTime());
        });
        final TripAssistantPanel tripAssistantPanel = buildTripAssistant(
                app, dayPlanViewModel,
                "Hi, I'm George. Ask me what to visit, what works in rain, or what fits your day.");
        final DaySwitcherPanel daySwitcherPanel =
                new DaySwitcherPanel(dayPlanViewModel, tripDayController);
        final PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel,
                daySwitcherPanel);
        final TrippyFrame frame = new TrippyFrame(
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
        loadTripAccessAsync(app, trip.getId(), tripAccessViewModel);
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
        final DashboardViewModel dashboardViewModel = new DashboardViewModel(
                new DashboardState(
                        "", null, "Offline ready",
                        "Create a trip to load weather for its destination."));
        final SearchViewModel searchViewModel = new SearchViewModel(
                new SearchState(app.activities.findAll(), ""));
        final BookmarksViewModel bookmarksViewModel = new BookmarksViewModel(
                new BookmarksState(Collections.emptyList()));
        final ActivitySelectionViewModel activitySelectionViewModel =
                new ActivitySelectionViewModel();
        final DayPlanViewModel dayPlanViewModel = new DayPlanViewModel(
                new DayPlanState(
                        "", Collections.emptyList(),
                        "Create a trip before planning or autoscheduling.", false));
        final CalendarViewModel calendarViewModel = new CalendarViewModel(
                dashboardViewModel, dayPlanViewModel);
        final ShareViewModel shareViewModel = new ShareViewModel(
                new ShareState("", "Create a trip before sharing.", false));
        final TripOptionsViewModel tripOptionsViewModel = new TripOptionsViewModel(
                new TripOptionsState(
                        "",
                        LocalDate.now().plusDays(1),
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)));
        final TripAccessViewModel tripAccessViewModel = new TripAccessViewModel();

        final AutoScheduleController autoScheduleController =
                buildAutoSchedule(app, dayPlanViewModel);
        final ShareTripPresenter sharePresenter = new ShareTripPresenter(shareViewModel);
        final ShareTripUseCase shareUseCase = new ShareTripUseCase(
                app.summary, app.trips, sharePresenter);
        final ShareTripController shareController = new ShareTripController(
                shareUseCase,
                () -> dayPlanViewModel.getState().getTripId());
        final ActivityDiscoveryPresenter discoveryPresenter = new ActivityDiscoveryPresenter(
                searchViewModel, bookmarksViewModel);
        final ActivityDiscoveryController discoveryController = new ActivityDiscoveryController(
                app.searchActivities, app.filterActivities,
                () -> dashboardViewModel.getState().getDestination(), discoveryPresenter);
        final BookmarkController bookmarkController = new BookmarkController(
                app.bookmarkActivity, app.removeBookmark,
                () -> dayPlanViewModel.getState().getTripId(), searchViewModel, discoveryPresenter);
        final ManualPlanPresenter manualPlanPresenter = new ManualPlanPresenter(
                dayPlanViewModel, searchViewModel);
        final ManualPlanController manualPlanController = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                () -> dayPlanViewModel.getState().getTripId(), manualPlanPresenter);
        final TripDayController tripDayController = new TripDayController(
                app.trips, () -> dayPlanViewModel.getState().getTripId(),
                manualPlanPresenter);

        final HeaderPanel headerPanel = new HeaderPanel(
                dashboardViewModel, dayPlanViewModel, shareController);
        final OverviewPanel overviewPanel = new OverviewPanel(
                dashboardViewModel, searchViewModel, bookmarksViewModel,
                dayPlanViewModel, activitySelectionViewModel);
        overviewPanel.setViewportPlacesLoader(
                new LoadViewportPlacesUseCase(app.places,
                        dashboardViewModel.getState().getDestination()));
        if (app.places instanceof DestinationGeocoder) {
            overviewPanel.setDestinationGeocoder((DestinationGeocoder) app.places);
        }
        final SearchPanel searchPanel = new SearchPanel(
                searchViewModel, discoveryController, bookmarkController,
                manualPlanController, activitySelectionViewModel,
                dayPlanViewModel, tripOptionsViewModel, tripAccessViewModel);
        final BookmarksPanel bookmarksPanel = new BookmarksPanel(
                bookmarksViewModel, bookmarkController,
                manualPlanController, activitySelectionViewModel,
                dayPlanViewModel, tripOptionsViewModel, tripAccessViewModel);
        final DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, autoScheduleController,
                        manualPlanController, activitySelectionViewModel,
                        tripAccessViewModel);
        final TripAssistantPanel tripAssistantPanel = buildTripAssistant(
                app, dayPlanViewModel,
                "Hi, I'm George. Create a trip, then ask me for activity recommendations.");
        final DaySwitcherPanel daySwitcherPanel =
                new DaySwitcherPanel(dayPlanViewModel, tripDayController);
        final PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel,
                daySwitcherPanel);
        final TrippyFrame frame = new TrippyFrame(
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
            final Trip demo = app.createTrip.executeAndReturn(new CreateTripInputData(
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
        final TripAssistantViewModel viewModel = new TripAssistantViewModel(
                new TripAssistantState(Collections.singletonList(new TripAssistantMessage(
                        TripAssistantMessage.Role.ASSISTANT, greeting)), false, ""));
        final TripAssistantPresenter presenter = new TripAssistantPresenter(viewModel);
        final TripAssistantInteractor interactor = new TripAssistantInteractor(
                app.trips, app.activities, app.weather, tripAssistantGateway(), presenter);
        final TripAssistantController controller = new TripAssistantController(
                interactor, () -> dayPlanViewModel.getState().getTripId(),
                presenter, viewModel, new SwingTaskRunner());
        return new TripAssistantPanel(viewModel, controller);
    }

    private TripAssistantGateway tripAssistantGateway() {
        final TripAssistantGateway offline = new OfflineTripAssistantGateway();
        final String mode = System.getProperty("trippy.chatbot.mode", "proxy");
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
        final String apiKey = DotEnv.get("OPENAI_API_KEY", "openai.api.key");
        if (apiKey == null) {
            return request -> {
                final TripAssistantDecision decision = offline.answer(request);
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
                final TripAssistantDecision decision = offline.answer(request);
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
        final SearchState searchCurrent = frame.getSearchViewModel().getState();
        final SearchState refreshed = searchStateFor(trip);
        // Merge the refreshed pool into whatever the map has already accumulated (viewport cells,
        // prior enrichment). Replacing wholesale lets a late or empty enrichment answer wipe the
        // markers the user can already see.
        final Map<String, Activity> merged = new LinkedHashMap<>();
        for (Activity activity : searchCurrent.getActivities()) merged.put(activity.getId(), activity);
        for (Activity activity : refreshed.getActivities()) merged.put(activity.getId(), activity);
        frame.getSearchViewModel().setState(new SearchState(
                new ArrayList<>(merged.values()),
                searchCurrent.getQuery(),
                refreshed.getBookmarkedIds(),
                refreshed.getScheduledIds(),
                searchCurrent.getSelectedActivityId(),
                searchCurrent.isLoading(),
                searchCurrent.getCategory(), searchCurrent.getMinimumRating(),
                searchCurrent.getType(), searchCurrent.getFeedback()));
        frame.getBookmarksViewModel().setState(new BookmarksState(trip.getBookmarkedActivities()));
        final DayPlanState dayPlanCurrent = frame.getDayPlanViewModel().getState();
        frame.getDayPlanViewModel().setState(new DayPlanState(
                trip.getId(),
                trip.getScheduledEvents(),
                "Seeded demo. Choose Autoschedule to arrange this day.",
                false,
                dayPlanCurrent.getHourlyWeather(),
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
        final AutoSchedulePresenter presenter = new AutoSchedulePresenter(dayPlanViewModel);
        final List<SoftPolicy> builtInPolicies = Arrays.asList(
                new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());
        final AutoScheduleInteractor interactor = new AutoScheduleInteractor(
                new DayScopedTripRepository(app.trips,
                        () -> dayPlanViewModel.getState().getActiveDayIndex()),
                new DistanceServiceTravelTimeEstimator(app.distances, tomtomConfigured()),
                new WeatherServiceContextGateway(app.weather),
                presenter,
                builtInPolicies,
                new ScheduleEngine());
        return new AutoScheduleController(interactor, dayPlanViewModel, new SwingTaskRunner());
    }

    /** Search and map view for a trip: every discovered place, tagged with its bookmark/schedule status. */
    private SearchState searchStateFor(Trip trip) {
        final Set<String> bookmarkedIds = new HashSet<>();
        for (Activity activity : trip.getBookmarkedActivities()) {
            bookmarkedIds.add(activity.getId());
        }
        final Set<String> scheduledIds = new HashSet<>();
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

    private void loadTripAccessAsync(AppContainer app, String tripId,
                                     TripAccessViewModel tripAccessViewModel) {
        if (app == null || app.account == null || tripId == null || tripId.trim().isEmpty()
                || tripAccessViewModel == null) {
            return;
        }
        final Thread worker = new Thread(() -> {
            try {
                final TripAccessLevel access = app.account.getMyTripAccess(tripId);
                SwingUtilities.invokeLater(() ->
                        tripAccessViewModel.setAccess(
                                access.canEditItinerary(), access.canManagePeople()));
            } catch (RuntimeException exception) {
                // Keep default editable until access loads, or user retries.
            }
        }, "Trip-Access-" + tripId);
        worker.setDaemon(true);
        worker.start();
    }

    private void refreshWeatherAsync(AppContainer app, Trip trip,
                                     DashboardViewModel dashboardViewModel,
                                     DayPlanViewModel dayPlanViewModel) {
        final Thread worker = new Thread(() -> {
            final List<WeatherWarning> hourlyWeather = weatherWarningsFor(app, trip);
            final WeatherWarning result = closestToTripStart(hourlyWeather, trip);
            final DashboardState state = new DashboardState(
                    trip.getDestination(),
                    trip.getDate(),
                    result.getWeatherCondition(),
                    result.getMessage());
            SwingUtilities.invokeLater(() -> {
                dashboardViewModel.setState(state);
                dayPlanViewModel.setState(
                        dayPlanViewModel.getState().withHourlyWeather(hourlyWeather));
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
            final long difference = Math.abs(Duration.between(
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
        final MockPlacesService mockPlaces = new MockPlacesService();
        final CachedPlacesRepository cachedPlaces = new CachedPlacesRepository();
        cachedPlaces.addAll(mockPlaces.findAll());
        final PlacesService places = useLivePlaces
                ? new CachingPlacesService(
                        new OpenStreetMapPlacesService(), cachedPlaces)
                : mockPlaces;

        final String persistence = System.getProperty("trippy.persistence.mode", "memory");
        final TripRepository trips;
        AccountService account = null;
        if ("supabase".equalsIgnoreCase(persistence)) {
            final AuthService auth = authSession;
            if (auth == null) {
                throw new IllegalStateException(
                        "Supabase persistence requires an AuthService instance");
            }
            final String url = DotEnv.get("TRIPPY_SUPABASE_URL", "trippy.supabase.url");
            final String anonKey = DotEnv.get("TRIPPY_SUPABASE_ANON_KEY", "trippy.supabase.anonKey");
            if (url == null || anonKey == null) {
                throw new IllegalStateException(
                        "Set TRIPPY_SUPABASE_URL and TRIPPY_SUPABASE_ANON_KEY in .env");
            }
            final PlaceHydrator hydrator = new PlaceHydrator(cachedPlaces, places, cachedPlaces);
            final InMemoryItineraryDataAccessObject local = new InMemoryItineraryDataAccessObject();
            final SupabaseItineraryDataAccess remote =
                    new SupabaseItineraryDataAccess(url, anonKey, auth, hydrator);
            trips = new DualModeItineraryDataAccess(local, remote, auth);
            account = new SupabaseAccountClient(url, anonKey, auth);
        } else {
            trips = new InMemoryItineraryDataAccessObject();
        }

        return new AppContainer(
                trips,
                places,
                cachedPlaces,
                new FastestModeDistanceService(new OsrmDistanceService(
                        () -> DotEnv.get("TOMTOM_API_KEY", "tomtom.api.key"))),
                weather,
                new DefaultActivityScoringPolicy(),
                (use_case.ports.ItineraryDataAccessInterface) trips,
                cachedPlaces,
                account);
    }

    /** Whether driving estimates can be traffic-aware. Read once at wiring time. */
    private static boolean tomtomConfigured() {
        return DotEnv.get("TOMTOM_API_KEY", "tomtom.api.key") != null;
    }
}
