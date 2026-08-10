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

    /**
     * Performs the s et st at e operation.
     * @param updatedState the u pd at ed st at e value
     */
    public void setState(SearchState updatedState) {
        final SearchState oldState = state;
        state = Objects.requireNonNull(updatedState, "Search state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    /**
     * Marks a discovered activity as focused in the sidebar.
     * @param activityId the a ct iv it yi d value
     */
    public void selectActivity(String activityId) {
        final SearchState current = state;
        setState(new SearchState(current.getActivities(), current.getQuery(),
                current.getBookmarkedIds(), current.getScheduledIds(), activityId,
                current.isLoading(), current.getCategory(), current.getMinimumRating(),
                current.getType(), current.getFeedback()));
    }

    /**
     * Toggles the loading indicator shown while places are being fetched.
     * @param loading the l oa di ng value
     */
    public void setLoading(boolean loading) {
        final SearchState current = state;
        if (current.isLoading() == loading) {
            return;
        }
        setState(new SearchState(current.getActivities(), current.getQuery(),
                current.getBookmarkedIds(), current.getScheduledIds(),
                current.getSelectedActivityId(), loading,
                current.getCategory(), current.getMinimumRating(),
                current.getType(), current.getFeedback()));
    }

    /**
     * Performs the a dd pr op er ty ch an ge li st en er operation.
     * @param listener the l is te ne r value
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    /**
     * Performs the r em ov ep ro pe rt yc ha ng el is te ne r operation.
     * @param listener the l is te ne r value
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
