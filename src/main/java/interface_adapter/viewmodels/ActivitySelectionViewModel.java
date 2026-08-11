package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/** Shared selection state for activity cards and the map. */
public final class ActivitySelectionViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private String selectedActivityId = "";

    public String getSelectedActivityId() {
        return selectedActivityId;
    }

    /**
     * Performs the s el ec t operation.
     * @param activityId the a ct iv it yi d value
     */
    public void select(String activityId) {
        final String updated = activityId == null ? "" : activityId.trim();
        final String previous = selectedActivityId;
        selectedActivityId = updated;
        changes.firePropertyChange("selectedActivityId", previous, updated);
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
