package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable ViewModel for create/edit trip setup. */
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

    /**
     * Replaces the current state and notifies every registered listener.
     *
     * @param updatedState the state to publish
     */
    public void setState(TripOptionsState updatedState) {
        final TripOptionsState oldState = state;
        state = Objects.requireNonNull(updatedState, "Trip-options state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    /**
     * Performs the s et fe ed ba ck operation.
     * @param error the e rr or value
     * @param message the m es sa ge value
     */
    public void setFeedback(String message, boolean error) {
        final TripOptionsState oldState = state;
        state = state.withFeedback(message, error);
        changes.firePropertyChange("feedback", oldState, state);
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
