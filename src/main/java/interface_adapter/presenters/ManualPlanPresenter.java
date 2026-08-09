package interface_adapter.presenters;

import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import java.util.HashSet;
import java.util.Set;
import javax.swing.SwingUtilities;

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

    public void presentSuccess(Trip trip, String message) {
        runOnEventThread(() -> {
            final DayPlanState currentPlan = dayPlan.getState();
            dayPlan.setState(new DayPlanState(
                    trip.getId(), trip.getScheduledEvents(), message, false,
                    currentPlan.getHourlyWeather(),
                    trip.getTripDates(), trip.getActiveDayIndex()));
            Set<String> scheduledIds = new HashSet<>();
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

    public void presentFailure(String message) {
        runOnEventThread(() -> {
            DayPlanState current = dayPlan.getState();
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
        } catch (Exception exception) {
            throw new IllegalStateException("Could not update Day Plan view", exception);
        }
    }
}
