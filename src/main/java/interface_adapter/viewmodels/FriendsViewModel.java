package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable friends-hub state. */
public final class FriendsViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private FriendsState state;

    public FriendsViewModel(FriendsState initialState) {
        state = Objects.requireNonNull(initialState, "Initial friends state is required");
    }

    public FriendsState getState() {
        return state;
    }

    public void setState(FriendsState updatedState) {
        FriendsState oldState = state;
        state = Objects.requireNonNull(updatedState, "Friends state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
