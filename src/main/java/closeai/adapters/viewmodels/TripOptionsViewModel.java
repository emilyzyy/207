package closeai.adapters.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable ViewModel for the Trip Options skeleton. */
public final class TripOptionsViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private TripOptionsState state;

    public TripOptionsViewModel(TripOptionsState initialState) {
        state = Objects.requireNonNull(
                initialState, "Initial trip-options state is required");
    }

    public TripOptionsState getState() {
        return state;
    }

    public void setState(TripOptionsState updatedState) {
        TripOptionsState oldState = state;
        state = Objects.requireNonNull(updatedState, "Trip-options state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
