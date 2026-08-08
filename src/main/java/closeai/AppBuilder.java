package closeai;

import closeai.adapters.controllers.ActivityDiscoveryController;
import closeai.adapters.controllers.AutoScheduleController;
import closeai.adapters.controllers.BookmarkController;
import closeai.adapters.controllers.ManualPlanController;
import closeai.adapters.controllers.ShareTripController;
import closeai.adapters.controllers.SwingTaskRunner;
import closeai.adapters.controllers.TripSetupController;
import closeai.adapters.controllers.TripAssistantController;
import closeai.adapters.presenters.ActivityDiscoveryPresenter;
import closeai.adapters.presenters.AutoSchedulePresenter;
import closeai.adapters.presenters.ManualPlanPresenter;
import closeai.adapters.presenters.ShareTripPresenter;
import closeai.adapters.presenters.TripSetupPresenter;
import closeai.adapters.presenters.TripAssistantPresenter;
import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.ActivitySelectionViewModel;
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
import closeai.adapters.viewmodels.TripAssistantState;
import closeai.adapters.viewmodels.TripAssistantViewModel;
import closeai.adapters.views.BookmarksPanel;
import closeai.adapters.views.CloseAIFrame;
import closeai.adapters.views.DayPlanPanel;
import closeai.adapters.views.HeaderPanel;
import closeai.adapters.views.OverviewPanel;
import closeai.adapters.views.PlannerPanel;
import closeai.adapters.views.SearchPanel;
import closeai.adapters.views.TripOptionsPanel;
import closeai.adapters.views.TripAssistantPanel;
import closeai.adapters.gateways.DistanceServiceTravelTimeEstimator;
import closeai.adapters.gateways.WeatherServiceContextGateway;
import closeai.application.AppContainer;
import closeai.application.PlaceHydrator;
import closeai.application.autoschedule.AutoScheduleInteractor;
import closeai.application.autoschedule.engine.ScheduleEngine;
import closeai.application.autoschedule.policy.DaylightPolicy;
import closeai.application.autoschedule.policy.MealWindowPolicy;
import closeai.application.autoschedule.policy.SoftPolicy;
import closeai.application.autoschedule.policy.WeatherSuitabilityPolicy;
import closeai.application.ports.AuthService;
import closeai.application.ports.PlacesService;
import closeai.application.ports.TripRepository;
import closeai.application.ports.TripAssistantGateway;
import closeai.application.ports.WeatherService;
import closeai.application.scheduling.DefaultActivityScoringPolicy;
import closeai.application.usecases.CreateTripInputData;
import closeai.application.usecases.TripSetupOutputData;
import closeai.application.tripassistant.TripAssistantDecision;
import closeai.application.tripassistant.TripAssistantInteractor;
import closeai.application.tripassistant.TripAssistantMessage;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import closeai.domain.valueobjects.WeatherSeverity;
import closeai.infrastructure.config.DotEnv;
import closeai.infrastructure.ai.FallbackTripAssistantGateway;
import closeai.infrastructure.ai.OfflineTripAssistantGateway;
import closeai.infrastructure.ai.OpenAiTripAssistantGateway;
import closeai.infrastructure.mock.MockPlacesService;
import closeai.infrastructure.mock.MockWeatherService;
import closeai.infrastructure.places.CachingPlacesService;
import closeai.infrastructure.places.NominatimPlacesService;
import closeai.infrastructure.persistence.CachedPlacesRepository;
import closeai.infrastructure.persistence.DualModeItineraryDataAccess;
import closeai.infrastructure.persistence.InMemoryItineraryDataAccessObject;
import closeai.infrastructure.routing.OsrmDistanceService;
import closeai.infrastructure.supabase.SupabaseItineraryDataAccess;
import closeai.infrastructure.weather.OpenMeteoWeatherService;
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
    public AppContainer build() {
        return build(null);
    }

    /** Builds the app; pass a signed-in {@link AuthService} when using Supabase persistence. */
    public AppContainer build(AuthService authSession) {
        String weatherMode = System.getProperty("closeai.weather.mode", "mock");
        String placesMode = System.getProperty("closeai.places.mode", "mock");
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
        ActivitySelectionViewModel activitySelectionViewModel =
                new ActivitySelectionViewModel();
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
        ManualPlanPresenter manualPlanPresenter = new ManualPlanPresenter(
                dayPlanViewModel, searchViewModel);
        ManualPlanController manualPlanController = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                () -> dayPlanViewModel.getState().getTripId(), manualPlanPresenter);

        HeaderPanel headerPanel = new HeaderPanel(
                dashboardViewModel, dayPlanViewModel, shareController);
        OverviewPanel overviewPanel = new OverviewPanel(
                dashboardViewModel, searchViewModel, bookmarksViewModel,
                dayPlanViewModel, activitySelectionViewModel);
        overviewPanel.setViewportPlacesLoader(
                (south, west, north, east, maxResults) ->
                        app.places.searchInBounds(south, west, north, east, maxResults));
        SearchPanel searchPanel = new SearchPanel(
                searchViewModel, discoveryController, bookmarkController,
                manualPlanController, activitySelectionViewModel);
        BookmarksPanel bookmarksPanel = new BookmarksPanel(
                bookmarksViewModel, bookmarkController,
                manualPlanController, activitySelectionViewModel);
        DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, autoScheduleController,
                        manualPlanController, activitySelectionViewModel);
        dayPlanPanel.setTripDefaults(trip.getStartTime(), trip.getEndTime(),
                trip.getTransportationMode());
        TripOptionsPanel tripOptionsPanel =
                new TripOptionsPanel(tripOptionsViewModel);
        TripAssistantPanel tripAssistantPanel = buildTripAssistant(
                app, dayPlanViewModel,
                "Hi, I'm George. Ask me what to visit, what works in rain, or what fits your day.");
        PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel, tripAssistantPanel, tripOptionsPanel);
        CloseAIFrame frame = new CloseAIFrame(
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
        ManualPlanPresenter manualPlanPresenter = new ManualPlanPresenter(
                dayPlanViewModel, searchViewModel);
        ManualPlanController manualPlanController = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                () -> dayPlanViewModel.getState().getTripId(), manualPlanPresenter);

        HeaderPanel headerPanel = new HeaderPanel(
                dashboardViewModel, dayPlanViewModel, shareController);
        OverviewPanel overviewPanel = new OverviewPanel(
                dashboardViewModel, searchViewModel, bookmarksViewModel,
                dayPlanViewModel, activitySelectionViewModel);
        overviewPanel.setViewportPlacesLoader(
                (south, west, north, east, maxResults) ->
                        app.places.searchInBounds(south, west, north, east, maxResults));
        SearchPanel searchPanel = new SearchPanel(
                searchViewModel, discoveryController, bookmarkController,
                manualPlanController, activitySelectionViewModel);
        BookmarksPanel bookmarksPanel = new BookmarksPanel(
                bookmarksViewModel, bookmarkController,
                manualPlanController, activitySelectionViewModel);
        DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, autoScheduleController,
                        manualPlanController, activitySelectionViewModel);
        TripOptionsPanel tripOptionsPanel =
                new TripOptionsPanel(tripOptionsViewModel, tripSetupController);
        TripAssistantPanel tripAssistantPanel = buildTripAssistant(
                app, dayPlanViewModel,
                "Hi, I'm George. Create a trip, then ask me for activity recommendations.");
        PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel, tripAssistantPanel, tripOptionsPanel);
        CloseAIFrame frame = new CloseAIFrame(
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
            tripSetupPresenter.presentSuccess(new TripSetupOutputData(demo, true));
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
        String mode = System.getProperty("closeai.chatbot.mode", "offline");
        if (!"openai".equalsIgnoreCase(mode)) {
            return offline;
        }
        String apiKey = DotEnv.get("OPENAI_API_KEY", "openai.api.key");
        if (apiKey == null) {
            return request -> {
                TripAssistantDecision decision = offline.answer(request);
                return new TripAssistantDecision(
                        decision.getIntent(), decision.getActivityIds(),
                        "OPENAI_API_KEY is not configured, so George used offline recommendations.");
            };
        }
        String model = DotEnv.get("OPENAI_MODEL", "closeai.openai.model");
        if (model == null) {
            model = "gpt-5.6-sol";
        }
        return new FallbackTripAssistantGateway(
                new OpenAiTripAssistantGateway(apiKey, model), offline);
    }

    /**
     * Pushes a refreshed snapshot of the trip into an already-built frame. Used by the gallery
     * flow to populate a newly created trip after its real activities finish loading.
     */
    public void refreshFrameForTrip(Trip trip, CloseAIFrame frame) {
        frame.getSearchViewModel().setState(searchStateFor(trip));
        frame.getBookmarksViewModel().setState(new BookmarksState(trip.getBookmarkedActivities()));
        DayPlanState current = frame.getDayPlanViewModel().getState();
        frame.getDayPlanViewModel().setState(new DayPlanState(
                trip.getId(),
                trip.getScheduledEvents(),
                "Seeded demo. Choose Autoschedule to arrange this day.",
                false,
                current.getHourlyWeather()));
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
                        current.isError(), hourlyWeather));
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
                        new NominatimPlacesService(), cachedPlaces)
                : mockPlaces;

        String persistence = System.getProperty("closeai.persistence.mode", "memory");
        TripRepository trips;
        if ("supabase".equalsIgnoreCase(persistence)) {
            AuthService auth = authSession;
            if (auth == null) {
                throw new IllegalStateException(
                        "Supabase persistence requires an AuthService instance");
            }
            String url = DotEnv.get("CLOSEAI_SUPABASE_URL", "closeai.supabase.url");
            String anonKey = DotEnv.get("CLOSEAI_SUPABASE_ANON_KEY", "closeai.supabase.anonKey");
            if (url == null || anonKey == null) {
                throw new IllegalStateException(
                        "Set CLOSEAI_SUPABASE_URL and CLOSEAI_SUPABASE_ANON_KEY in .env");
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
                new OsrmDistanceService(),
                weather,
                new DefaultActivityScoringPolicy(),
                (closeai.application.ports.ItineraryDataAccessInterface) trips);
    }
}
