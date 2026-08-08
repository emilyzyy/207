package trippy.adapters.viewmodels;

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

    public TripAssistantState getState() { return state; }

    public void setState(TripAssistantState updatedState) {
        TripAssistantState oldState = state;
        state = Objects.requireNonNull(updatedState, "Assistant state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
