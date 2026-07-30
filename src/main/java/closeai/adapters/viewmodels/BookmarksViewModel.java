package closeai.adapters.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable ViewModel for the Bookmarks skeleton. */
public final class BookmarksViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private BookmarksState state;

    public BookmarksViewModel(BookmarksState initialState) {
        state = Objects.requireNonNull(initialState, "Initial bookmarks state is required");
    }

    public BookmarksState getState() {
        return state;
    }

    public void setState(BookmarksState updatedState) {
        BookmarksState oldState = state;
        state = Objects.requireNonNull(updatedState, "Bookmarks state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
