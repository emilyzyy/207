package interface_adapter.viewmodels;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/** Whether the signed-in user may change the open trip (vs view-only access). */
public final class TripAccessViewModel {
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private boolean canEditItinerary = true;
    private boolean canManagePeople = false;

    /**
     * Performs the c an ed it it in er ar y operation.
     * @return the result of the operation
     */
    public boolean canEditItinerary() {
        return canEditItinerary;
    }

    /**
     * Performs the c an ma na ge pe op le operation.
     * @return the result of the operation
     */
    public boolean canManagePeople() {
        return canManagePeople;
    }

    /**
     * Performs the s et ac ce ss operation.
     * @param canManagePeople the c an ma na ge pe op le value
     * @param canEditItinerary the c an ed it it in er ar y value
     */
    public void setAccess(boolean canEditItinerary, boolean canManagePeople) {
        final boolean editChanged = this.canEditItinerary != canEditItinerary;
        final boolean manageChanged = this.canManagePeople != canManagePeople;
        this.canEditItinerary = canEditItinerary;
        this.canManagePeople = canManagePeople;
        if (editChanged || manageChanged) {
            support.firePropertyChange("access", null, this);
        }
    }

    /**
     * Performs the a dd pr op er ty ch an ge li st en er operation.
     * @param listener the l is te ne r value
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }
}
