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
     * Performs the s et st at e operation.
     * @param updatedState the u pd at ed st at e value
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
