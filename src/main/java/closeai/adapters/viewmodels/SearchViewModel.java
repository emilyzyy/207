package closeai.adapters.viewmodels;

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

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
