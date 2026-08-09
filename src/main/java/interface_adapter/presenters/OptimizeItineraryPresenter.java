package interface_adapter.presenters;

import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import use_case.usecases.OptimizeItineraryOutputBoundary;
import use_case.usecases.OptimizeItineraryOutputData;

/** Converts optimize output into the state shared by Day Plan and Calendar. */
public final class OptimizeItineraryPresenter implements OptimizeItineraryOutputBoundary {
    private final DayPlanViewModel viewModel;

    public OptimizeItineraryPresenter(DayPlanViewModel viewModel) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Day-plan ViewModel is required");
        }
        this.viewModel = viewModel;
    }

    @Override
    public void presentSuccess(OptimizeItineraryOutputData outputData) {
        DayPlanState current = viewModel.getState();
        viewModel.setState(new DayPlanState(
                outputData.getTrip().getId(),
                outputData.getTrip().getScheduledEvents(),
                outputData.getMessage(),
                false,
                current.getHourlyWeather(),
                outputData.getTrip().getTripDates(),
                outputData.getTrip().getActiveDayIndex()));
    }

    @Override
    public void presentFailure(String errorMessage) {
        DayPlanState current = viewModel.getState();
        viewModel.setState(new DayPlanState(
                current.getTripId(),
                current.getEvents(),
                errorMessage == null ? "Unable to compact itinerary" : errorMessage,
                true,
                current.getHourlyWeather(),
                current.getTripDates(),
                current.getActiveDayIndex()));
    }
}
