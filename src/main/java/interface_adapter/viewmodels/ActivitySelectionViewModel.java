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
