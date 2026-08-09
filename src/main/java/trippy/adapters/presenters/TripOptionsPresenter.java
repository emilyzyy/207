package trippy.adapters.presenters;

import trippy.adapters.viewmodels.DashboardState;
import trippy.adapters.viewmodels.DashboardViewModel;
import trippy.adapters.viewmodels.DayPlanState;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.adapters.viewmodels.TripOptionsState;
import trippy.adapters.viewmodels.TripOptionsViewModel;
import trippy.application.usecases.TripOptionsOutputBoundary;
import trippy.domain.entities.Trip;

/** Updates the existing trip, header, and Day Plan state after the options popup saves. */
public final class TripOptionsPresenter implements TripOptionsOutputBoundary {
    private final DashboardViewModel dashboard;
    private final DayPlanViewModel dayPlan;
    private final TripOptionsViewModel options;

    public TripOptionsPresenter(DashboardViewModel dashboard,
                                DayPlanViewModel dayPlan,
                                TripOptionsViewModel options) {
        if (dashboard == null || dayPlan == null || options == null) {
            throw new IllegalArgumentException("Trip Options ViewModels are required");
        }
        this.dashboard = dashboard;
        this.dayPlan = dayPlan;
        this.options = options;
    }

    @Override
    public void presentSuccess(Trip trip, String message) {
        DashboardState currentDashboard = dashboard.getState();
        dashboard.setState(new DashboardState(
                trip.getDestination(), trip.getDate(),
                currentDashboard.getWeatherCondition(), currentDashboard.getWeatherMessage()));
        DayPlanState currentPlan = dayPlan.getState();
        dayPlan.setState(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), message, false,
                currentPlan.getHourlyWeather(), trip.getTripDates(), trip.getActiveDayIndex()));
        options.setState(TripOptionsState.fromTrip(trip, message, false));
    }

    @Override
    public void presentFailure(String message) {
        options.setFeedback(message == null ? "Trip options could not be saved" : message, true);
    }
}
