package closeai.adapters.viewmodels;

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

    public void setState(DayPlanState updatedState) {
        DayPlanState oldState = state;
        state = Objects.requireNonNull(updatedState, "Day-plan state is required");
        changes.firePropertyChange("state", oldState, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
