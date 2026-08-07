package closeai.adapters.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/** Shared selection state for activity cards and the map. */
public final class ActivitySelectionViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private String selectedActivityId = "";

    public String getSelectedActivityId() {
        return selectedActivityId;
    }

    public void select(String activityId) {
        String updated = activityId == null ? "" : activityId.trim();
        String previous = selectedActivityId;
        selectedActivityId = updated;
        changes.firePropertyChange("selectedActivityId", previous, updated);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}
