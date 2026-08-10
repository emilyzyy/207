package use_case.ports;

import java.util.List;

import entity.entities.Activity;

/** Write-side port for persisting discovered places. */
public interface PlacesWriter {
    /**
     * Performs the a dd al l operation.
     * @param activities the a ct iv it ie s value
     */
    void addAll(List<Activity> activities);
}
