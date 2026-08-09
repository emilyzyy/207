package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable ViewModel for the Search skeleton. */
public final class SearchViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private SearchState state;

    public SearchViewModel(SearchState initialState) {
        state = Objects.requireNonNull(initialState, "Initial search state is required");
    }

    public SearchState getState() {
        return state;
    }

    public void setState(SearchState updatedState) {
        SearchState oldState = state;
        state = Objects.requireNonNull(updatedState, "Search state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    /** Marks a discovered activity as focused in the sidebar. */
    public void selectActivity(String activityId) {
        SearchState current = state;
        setState(new SearchState(current.getActivities(), current.getQuery(),
                current.getBookmarkedIds(), current.getScheduledIds(), activityId,
                current.isLoading(), current.getCategory(), current.getMinimumRating(),
                current.getType(), current.getFeedback()));
    }

    /** Toggles the loading indicator shown while places are being fetched. */
    public void setLoading(boolean loading) {
        SearchState current = state;
        if (current.isLoading() == loading) return;
        setState(new SearchState(current.getActivities(), current.getQuery(),
                current.getBookmarkedIds(), current.getScheduledIds(),
                current.getSelectedActivityId(), loading,
                current.getCategory(), current.getMinimumRating(),
                current.getType(), current.getFeedback()));
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
