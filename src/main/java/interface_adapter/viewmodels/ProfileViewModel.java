package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable profile state. */
public final class ProfileViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private ProfileState state;

    public ProfileViewModel(ProfileState initialState) {
        state = Objects.requireNonNull(initialState, "Initial profile state is required");
    }

    public ProfileState getState() {
        return state;
    }

    public void setState(ProfileState updatedState) {
        ProfileState oldState = state;
        state = Objects.requireNonNull(updatedState, "Profile state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
