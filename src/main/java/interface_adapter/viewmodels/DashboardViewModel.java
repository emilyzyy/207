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
     * Performs the s et st at e operation.
     * @param updatedState the u pd at ed st at e value
     */
    public void setState(DashboardState updatedState) {
        final DashboardState oldState = state;
        state = Objects.requireNonNull(updatedState, "Dashboard state is required");
        changes.firePropertyChange("state", oldState, state);
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
