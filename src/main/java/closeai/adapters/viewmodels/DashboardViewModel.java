package closeai.adapters.viewmodels;

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

    public void setState(DashboardState updatedState) {
        DashboardState oldState = state;
        state = Objects.requireNonNull(updatedState, "Dashboard state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
