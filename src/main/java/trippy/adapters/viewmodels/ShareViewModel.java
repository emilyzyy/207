package trippy.adapters.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable share-preview state. */
public final class ShareViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private ShareState state;

    public ShareViewModel(ShareState initialState) {
        state = Objects.requireNonNull(initialState, "Initial share state is required");
    }

    public ShareState getState() {
        return state;
    }

    public void setState(ShareState updatedState) {
        ShareState oldState = state;
        state = Objects.requireNonNull(updatedState, "Share state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
