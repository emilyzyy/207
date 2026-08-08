package closeai.adapters.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/** Whether the signed-in user may change the open trip (vs view-only access). */
public final class TripAccessViewModel {
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private boolean canEditItinerary = true;
    private boolean canManagePeople = false;

    public boolean canEditItinerary() {
        return canEditItinerary;
    }

    public boolean canManagePeople() {
        return canManagePeople;
    }

    public void setAccess(boolean canEditItinerary, boolean canManagePeople) {
        boolean editChanged = this.canEditItinerary != canEditItinerary;
        boolean manageChanged = this.canManagePeople != canManagePeople;
        this.canEditItinerary = canEditItinerary;
        this.canManagePeople = canManagePeople;
        if (editChanged || manageChanged) {
            support.firePropertyChange("access", null, this);
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }
}
