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
     * Performs the s et st at e operation.
     * @param updatedState the u pd at ed st at e value
     */
    public void setState(TripAssistantState updatedState) {
        final TripAssistantState oldState = state;
        state = Objects.requireNonNull(updatedState, "Assistant state is required");
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
