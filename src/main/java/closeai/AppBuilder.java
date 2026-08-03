package closeai;

import closeai.adapters.controllers.OptimizeItineraryController;
import closeai.adapters.controllers.ShareTripController;
import closeai.adapters.controllers.TripSetupController;
import closeai.adapters.presenters.OptimizeItineraryPresenter;
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
import closeai.application.AppContainer;
import closeai.application.ports.PlacesService;
import closeai.application.ports.WeatherService;
import closeai.application.scheduling.DefaultActivityScoringPolicy;
import closeai.application.usecases.OptimizeItineraryInteractor;
import closeai.domain.valueobjects.TransportationMode;
import closeai.infrastructure.mock.MockDistanceService;
import closeai.infrastructure.mock.MockPlacesService;
import closeai.infrastructure.mock.MockWeatherService;
import closeai.infrastructure.places.CachingPlacesService;
import closeai.infrastructure.places.NominatimPlacesService;
import closeai.infrastructure.persistence.CachedPlacesRepository;
import closeai.infrastructure.persistence.InMemoryItineraryDataAccessObject;
import closeai.infrastructure.weather.OpenMeteoWeatherService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

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

    /** Builds Swing with no seeded trip; Trip Setup owns creation of the active trip. */
    public CloseAIFrame buildSwingApplication() {
        return buildSwingApplication(build());
    }

    /** Builds Swing around an injected application container for deterministic integration tests. */
    public CloseAIFrame buildSwingApplication(AppContainer app) {
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
                        "Create a trip before planning or optimizing.", false));
        CalendarViewModel calendarViewModel = new CalendarViewModel(
                dashboardViewModel, dayPlanViewModel);
        ShareViewModel shareViewModel = new ShareViewModel(
                new ShareState("", "Create a trip before sharing.", false));
        TripOptionsViewModel tripOptionsViewModel = new TripOptionsViewModel(
                new TripOptionsState(
                        "",
                        LocalDate.now().plusDays(1),
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        TransportationMode.WALKING));

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

        OptimizeItineraryPresenter optimizePresenter =
                new OptimizeItineraryPresenter(dayPlanViewModel);
        OptimizeItineraryInteractor optimizeInteractor =
                new OptimizeItineraryInteractor(app.trips, optimizePresenter);
        OptimizeItineraryController optimizeController =
                new OptimizeItineraryController(optimizeInteractor, dayPlanViewModel);
        ShareTripPresenter sharePresenter = new ShareTripPresenter(shareViewModel);
        ShareTripController shareController = new ShareTripController(
                app.share,
                () -> dayPlanViewModel.getState().getTripId(),
                sharePresenter);

        HeaderPanel headerPanel = new HeaderPanel(
                dashboardViewModel, dayPlanViewModel, shareController);
        OverviewPanel overviewPanel = new OverviewPanel(dashboardViewModel, searchViewModel);
        SearchPanel searchPanel = new SearchPanel(searchViewModel);
        BookmarksPanel bookmarksPanel = new BookmarksPanel(bookmarksViewModel);
        DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, optimizeController);
        TripOptionsPanel tripOptionsPanel =
                new TripOptionsPanel(tripOptionsViewModel, tripSetupController);
        PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel, tripOptionsPanel);
        return new CloseAIFrame(
                headerPanel,
                overviewPanel,
                plannerPanel,
                dayPlanPanel,
                dayPlanViewModel,
                calendarViewModel,
                shareViewModel);
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
                new MockDistanceService(),
                weather,
                new DefaultActivityScoringPolicy(),
                itineraries);
    }
}
