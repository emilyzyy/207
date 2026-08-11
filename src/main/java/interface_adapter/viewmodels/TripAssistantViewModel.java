package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable state holder for George's floating chat widget. */
public final class TripAssistantViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private TripAssistantState state;

    public TripAssistantViewModel(TripAssistantState initialState) {
        state = Objects.requireNonNull(initialState, "Initial assistant state is required");
    }

    public TripAssistantState getState() {
        return state;
    }

    /**
     * Replaces the current state and notifies every registered listener.
     *
     * @param updatedState the state to publish
     */
    public void setState(TripAssistantState updatedState) {
        final TripAssistantState oldState = state;
        state = Objects.requireNonNull(updatedState, "Assistant state is required");
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
