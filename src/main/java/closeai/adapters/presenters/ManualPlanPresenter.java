package closeai.adapters.presenters;

import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
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
            dayPlan.setState(new DayPlanState(
                    trip.getId(), trip.getScheduledEvents(), message, false));
            Set<String> scheduledIds = new HashSet<>();
            for (ScheduledEvent event : trip.getScheduledEvents()) {
                if (event.getActivity() != null) {
                    scheduledIds.add(event.getActivity().getId());
                }
            }
            SearchState current = search.getState();
            search.setState(new SearchState(
                    current.getActivities(), current.getQuery(), current.getBookmarkedIds(),
                    scheduledIds, current.getCategory(), current.getMinimumRating(),
                    current.getType(), current.getFeedback()));
        });
    }

    public void presentFailure(String message) {
        runOnEventThread(() -> {
            DayPlanState current = dayPlan.getState();
            dayPlan.setState(new DayPlanState(
                    current.getTripId(), current.getEvents(),
                    message == null ? "Unable to update the Day Plan" : message, true));
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
