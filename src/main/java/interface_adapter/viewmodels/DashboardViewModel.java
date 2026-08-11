package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable ViewModel for header and overview state. */
public final class DashboardViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private DashboardState state;

    public DashboardViewModel(DashboardState initialState) {
        state = Objects.requireNonNull(initialState, "Initial dashboard state is required");
    }

    public DashboardState getState() {
        return state;
    }

    /**
     * Replaces the current state and notifies every registered listener.
     *
     * @param updatedState the state to publish
     */
    public void setState(DashboardState updatedState) {
        final DashboardState oldState = state;
        state = Objects.requireNonNull(updatedState, "Dashboard state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    /**
     * Registers a listener notified whenever this view model's state is replaced.
     *
     * @param listener the listener to notify
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    /**
     * Stops notifying the given listener of state changes.
     *
     * @param listener the listener to stop notifying
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
