package closeai.adapters.presenters;

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
import closeai.application.usecases.GetWeatherWarningUseCase;
import closeai.application.usecases.SearchActivitiesUseCase;
import closeai.application.usecases.TripSetupOutputBoundary;
import closeai.application.usecases.TripSetupOutputData;
import closeai.domain.entities.Activity;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import java.util.List;
import javax.swing.SwingWorker;

/**
 * Updates every active-trip ViewModel immediately, then loads destination data off the EDT.
 */
public final class TripSetupPresenter implements TripSetupOutputBoundary {
    private final DashboardViewModel dashboard;
    private final SearchViewModel search;
    private final BookmarksViewModel bookmarks;
    private final DayPlanViewModel dayPlan;
    private final TripOptionsViewModel options;
    private final GetWeatherWarningUseCase weatherWarning;
    private final SearchActivitiesUseCase searchActivities;

    public TripSetupPresenter(
            DashboardViewModel dashboard,
            SearchViewModel search,
            BookmarksViewModel bookmarks,
            DayPlanViewModel dayPlan,
            TripOptionsViewModel options,
            GetWeatherWarningUseCase weatherWarning,
            SearchActivitiesUseCase searchActivities) {
        if (dashboard == null || search == null || bookmarks == null
                || dayPlan == null || options == null) {
            throw new IllegalArgumentException("Trip setup ViewModels are required");
        }
        this.dashboard = dashboard;
        this.search = search;
        this.bookmarks = bookmarks;
        this.dayPlan = dayPlan;
        this.options = options;
        this.weatherWarning = weatherWarning;
        this.searchActivities = searchActivities;
    }

    @Override
    public void presentSuccess(TripSetupOutputData outputData) {
        Trip trip = outputData.getTrip();
        String action = outputData.isCreated() ? "Trip created" : "Trip options saved";

        dashboard.setState(new DashboardState(
                trip.getDestination(),
                trip.getDate(),
                "Loading weather…",
                "Weather and places refresh in the background."));
        bookmarks.setState(new BookmarksState(trip.getBookmarkedActivities()));
        dayPlan.setState(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(),
                action + ". Add activities before optimizing.", false));
        options.setState(TripOptionsState.fromTrip(trip, action + " successfully.", false));
        refreshDestinationData(trip);
    }

    @Override
    public void presentFailure(String errorMessage) {
        options.setFeedback(
                errorMessage == null || errorMessage.trim().isEmpty()
                        ? "Unable to save trip options" : errorMessage,
                true);
    }

    private void refreshDestinationData(Trip trip) {
        if (weatherWarning == null && searchActivities == null) {
            return;
        }
        search.setLoading(true);
        new SwingWorker<DestinationData, Void>() {
            @Override
            protected DestinationData doInBackground() {
                WeatherWarning warning = null;
                List<Activity> activities = search.getState().getActivities();
                try {
                    if (weatherWarning != null) {
                        warning = weatherWarning.execute(trip.getId());
                    }
                } catch (RuntimeException ignored) {
                    // Weather is optional UI enrichment; trip creation remains successful.
                }
                try {
                    if (searchActivities != null) {
                        List<Activity> discovered =
                                searchActivities.execute(trip.getDestination(), "");
                        if (discovered != null && !discovered.isEmpty()) {
                            activities = discovered;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // Keep the current offline/cache list if live place lookup fails.
                }
                return new DestinationData(warning, activities);
            }

            @Override
            protected void done() {
                search.setLoading(false);
                if (!trip.getId().equals(options.getState().getTripId())) {
                    return;
                }
                try {
                    DestinationData data = get();
                    WeatherWarning warning = data.warning;
                    dashboard.setState(new DashboardState(
                            trip.getDestination(),
                            trip.getDate(),
                            warning == null ? "Weather unavailable"
                                    : warning.getWeatherCondition(),
                            warning == null ? "Trip saved; weather could not be refreshed."
                                    : warning.getMessage()));
                    search.setState(new SearchState(data.activities, ""));
                } catch (Exception exception) {
                    dashboard.setState(new DashboardState(
                            trip.getDestination(), trip.getDate(),
                            "Weather unavailable",
                            "Trip saved; destination data could not be refreshed."));
                }
            }
        }.execute();
    }

    private static final class DestinationData {
        private final WeatherWarning warning;
        private final List<Activity> activities;

        private DestinationData(WeatherWarning warning, List<Activity> activities) {
            this.warning = warning;
            this.activities = activities;
        }
    }
}
