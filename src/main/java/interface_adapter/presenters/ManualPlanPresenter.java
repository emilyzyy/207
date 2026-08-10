package interface_adapter.presenters;

import java.util.HashSet;
import java.util.Set;

import javax.swing.SwingUtilities;

import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;

/** Keeps the Day Plan, Calendar, and activity cards synchronized after manual edits. */
public final class ManualPlanPresenter {
    private final DayPlanViewModel dayPlan;
    private final SearchViewModel search;

    public ManualPlanPresenter(DayPlanViewModel dayPlan, SearchViewModel search) {
        if (dayPlan == null || search == null) {
            throw new IllegalArgumentException("Manual plan ViewModels are required");
        }
        this.dayPlan = dayPlan;
        this.search = search;
    }

    /**
     * Performs the p re se nt su cc es s operation.
     * @param message the m es sa ge value
     * @param trip the t ri p value
     */
    public void presentSuccess(Trip trip, String message) {
        runOnEventThread(() -> {
            final DayPlanState currentPlan = dayPlan.getState();
            dayPlan.setState(new DayPlanState(
                    trip.getId(), trip.getScheduledEvents(), message, false,
                    currentPlan.getHourlyWeather(),
                    trip.getTripDates(), trip.getActiveDayIndex()));
            final Set<String> scheduledIds = new HashSet<>();
            for (ScheduledEvent event : trip.getScheduledEvents()) {
                if (event.getActivity() != null) {
                    scheduledIds.add(event.getActivity().getId());
                }
            }
            final SearchState currentSearch = search.getState();
            search.setState(new SearchState(
                    currentSearch.getActivities(), currentSearch.getQuery(),
                    currentSearch.getBookmarkedIds(), scheduledIds,
                    currentSearch.getCategory(), currentSearch.getMinimumRating(),
                    currentSearch.getType(), currentSearch.getFeedback()));
        });
    }

    /**
     * Performs the p re se nt fa il ur e operation.
     * @param message the m es sa ge value
     */
    public void presentFailure(String message) {
        runOnEventThread(() -> {
            final DayPlanState current = dayPlan.getState();
            dayPlan.setState(new DayPlanState(
                    current.getTripId(), current.getEvents(),
                    message == null ? "Unable to update the Day Plan" : message, true,
                    current.getHourlyWeather(), current.getTripDates(),
                    current.getActiveDayIndex()));
        });
    }

    private static void runOnEventThread(Runnable update) {
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(update);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not update Day Plan view", exception);
        }
    }
}
