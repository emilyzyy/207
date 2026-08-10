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
     * Performs the s et st at e operation.
     * @param updatedState the u pd at ed st at e value
     */
    public void setState(DayPlanState updatedState) {
        final DayPlanState oldState = state;
        state = Objects.requireNonNull(updatedState, "Day-plan state is required");
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
