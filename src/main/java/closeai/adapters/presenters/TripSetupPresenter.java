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
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
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
        search.setState(searchStateFor(trip, search.getState().getActivities()));
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
                List<WeatherWarning> hourlyWeather = Collections.emptyList();
                List<Activity> activities = search.getState().getActivities();
                try {
                    if (weatherWarning != null) {
                        hourlyWeather = weatherWarning.executeHourly(trip.getId());
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
                return new DestinationData(hourlyWeather, activities);
            }

            @Override
            protected void done() {
                search.setLoading(false);
                if (!trip.getId().equals(options.getState().getTripId())) {
                    return;
                }
                try {
                    DestinationData data = get();
                    WeatherWarning warning = closestToTripStart(
                            data.hourlyWeather, trip);
                    dashboard.setState(new DashboardState(
                            trip.getDestination(),
                            trip.getDate(),
                            warning == null ? "Weather unavailable"
                                    : warning.getWeatherCondition(),
                            warning == null ? "Trip saved; weather could not be refreshed."
                                    : warning.getMessage()));
                    DayPlanState currentPlan = dayPlan.getState();
                    dayPlan.setState(new DayPlanState(
                            currentPlan.getTripId(), currentPlan.getEvents(),
                            currentPlan.getMessage(), currentPlan.isError(),
                            data.hourlyWeather));
                    search.setState(searchStateFor(trip, data.activities));
                } catch (Exception exception) {
                    dashboard.setState(new DashboardState(
                            trip.getDestination(), trip.getDate(),
                            "Weather unavailable",
                            "Trip saved; destination data could not be refreshed."));
                }
            }
        }.execute();
    }

    private static SearchState searchStateFor(Trip trip, List<Activity> activities) {
        Set<String> bookmarkedIds = new HashSet<>();
        for (Activity activity : trip.getBookmarkedActivities()) {
            bookmarkedIds.add(activity.getId());
        }
        Set<String> scheduledIds = new HashSet<>();
        trip.getScheduledEvents().forEach(event -> {
            if (event.getActivity() != null) {
                scheduledIds.add(event.getActivity().getId());
            }
        });
        return new SearchState(activities, "", bookmarkedIds, scheduledIds);
    }

    private WeatherWarning closestToTripStart(List<WeatherWarning> hourlyWeather, Trip trip) {
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
        return closest;
    }

    private static final class DestinationData {
        private final List<WeatherWarning> hourlyWeather;
        private final List<Activity> activities;

        private DestinationData(
                List<WeatherWarning> hourlyWeather, List<Activity> activities) {
            this.hourlyWeather = hourlyWeather == null
                    ? Collections.emptyList() : hourlyWeather;
            this.activities = activities;
        }
    }
}
