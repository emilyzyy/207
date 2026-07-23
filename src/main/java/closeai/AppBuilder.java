package closeai;

import closeai.adapters.controllers.OptimizeItineraryController;
import closeai.adapters.presenters.OptimizeItineraryPresenter;
import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
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
import closeai.application.ports.WeatherService;
import closeai.application.scheduling.DefaultActivityScoringPolicy;
import closeai.application.usecases.OptimizeItineraryInteractor;
import closeai.domain.entities.Activity;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.TransportationMode;
import closeai.infrastructure.mock.MockDistanceService;
import closeai.infrastructure.mock.MockPlacesService;
import closeai.infrastructure.mock.MockWeatherService;
import closeai.infrastructure.persistence.InMemoryItineraryDataAccessObject;
import closeai.infrastructure.weather.OpenMeteoWeatherService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Outer composition root for selecting infrastructure without leaking it into application code. */
public final class AppBuilder {
    public AppContainer build() {
        String weatherMode = System.getProperty("closeai.weather.mode", "mock");
        return "open-meteo".equalsIgnoreCase(weatherMode) ? buildLive() : buildOffline();
    }

    public AppContainer buildOffline() {
        return buildWithWeather(new MockWeatherService());
    }

    public AppContainer buildLive() {
        return buildWithWeather(new OpenMeteoWeatherService());
    }

    /**
     * Builds the milestone Swing application around a replaceable seeded Trip aggregate.
     *
     * <p>The UI reads domain-backed state, so a future Create Trip flow can supply the active
     * trip without changing the view structure.</p>
     */
    public CloseAIFrame buildSwingApplication() {
        AppContainer app = build();
        Trip trip = seedDemoTrip(app);
        WeatherWarning warning = app.weatherWarning.execute(trip.getId());
        List<Activity> activities = app.activities.findAll();

        DashboardViewModel dashboardViewModel = new DashboardViewModel(
                new DashboardState(
                        trip.getDestination(),
                        trip.getDate(),
                        warning.getWeatherCondition(),
                        warning.getMessage()));
        SearchViewModel searchViewModel = new SearchViewModel(
                new SearchState(activities, ""));
        BookmarksViewModel bookmarksViewModel = new BookmarksViewModel(
                new BookmarksState(trip.getBookmarkedActivities()));
        DayPlanViewModel dayPlanViewModel = new DayPlanViewModel(
                new DayPlanState(
                        trip.getId(),
                        trip.getScheduledEvents(),
                        "Seeded demo · optimizer uses Day Plan activities only",
                        false));
        TripOptionsViewModel tripOptionsViewModel = new TripOptionsViewModel(
                new TripOptionsState(
                        trip.getDestination(),
                        trip.getDate(),
                        trip.getStartTime(),
                        trip.getEndTime(),
                        trip.getTransportationMode()));

        OptimizeItineraryPresenter optimizePresenter =
                new OptimizeItineraryPresenter(dayPlanViewModel);
        OptimizeItineraryInteractor optimizeInteractor =
                new OptimizeItineraryInteractor(app.trips, optimizePresenter);
        OptimizeItineraryController optimizeController =
                new OptimizeItineraryController(optimizeInteractor, trip.getId());

        HeaderPanel headerPanel = new HeaderPanel(dashboardViewModel);
        OverviewPanel overviewPanel = new OverviewPanel(dashboardViewModel);
        SearchPanel searchPanel = new SearchPanel(searchViewModel);
        BookmarksPanel bookmarksPanel = new BookmarksPanel(bookmarksViewModel);
        DayPlanPanel dayPlanPanel =
                new DayPlanPanel(dayPlanViewModel, optimizeController);
        TripOptionsPanel tripOptionsPanel =
                new TripOptionsPanel(tripOptionsViewModel);
        PlannerPanel plannerPanel = new PlannerPanel(
                searchPanel, bookmarksPanel, dayPlanPanel, tripOptionsPanel);
        return new CloseAIFrame(
                headerPanel,
                overviewPanel,
                plannerPanel,
                dayPlanPanel,
                dayPlanViewModel);
    }

    private AppContainer buildWithWeather(WeatherService weather) {
        InMemoryItineraryDataAccessObject itineraries = new InMemoryItineraryDataAccessObject();
        MockPlacesService places = new MockPlacesService();
        return new AppContainer(itineraries, places, places, new MockDistanceService(), weather,
                new DefaultActivityScoringPolicy(), itineraries);
    }

    private Trip seedDemoTrip(AppContainer app) {
        Trip created = app.createTrip.execute(
                "Toronto",
                LocalDate.of(2026, 7, 23),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                TransportationMode.WALKING);

        app.addActivityToPlan.execute(created.getId(), "rom", LocalTime.of(10, 0));
        app.addActivityToPlan.execute(created.getId(), "pai", LocalTime.of(12, 45));
        app.addActivityToPlan.execute(created.getId(), "cn-tower", LocalTime.of(15, 0));

        // These deliberately include an activity outside the schedule. The active optimizer
        // must ignore bookmarks and compact only the three current Day Plan activities.
        app.bookmarkActivity.execute(created.getId(), "islands");
        app.bookmarkActivity.execute(created.getId(), "ago");
        return app.trips.findById(created.getId())
                .orElseThrow(() -> new IllegalStateException("Seeded demo trip was not saved"));
    }
}
