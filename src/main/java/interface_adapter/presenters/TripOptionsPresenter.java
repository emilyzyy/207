package interface_adapter.presenters;

import interface_adapter.viewmodels.DashboardState;
import interface_adapter.viewmodels.DashboardViewModel;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.TripOptionsState;
import interface_adapter.viewmodels.TripOptionsViewModel;
import use_case.usecases.TripOptionsOutputBoundary;
import entity.entities.Trip;

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
