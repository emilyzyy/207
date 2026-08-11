package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/** Observable ViewModel for the shared day-plan schedule state. */
public final class DayPlanViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private DayPlanState state;

    public DayPlanViewModel(DayPlanState initialState) {
        state = Objects.requireNonNull(initialState, "Initial day-plan state is required");
    }

    public DayPlanState getState() {
        return state;
    }

    /**
     * Replaces the current state and notifies every registered listener.
     *
     * @param updatedState the state to publish
     */
    public void setState(DayPlanState updatedState) {
        final DayPlanState oldState = state;
        state = Objects.requireNonNull(updatedState, "Day-plan state is required");
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
