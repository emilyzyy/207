package use_case.ports;

import java.util.List;

import entity.entities.Activity;

/** Write-side port for persisting discovered places. */
public interface PlacesWriter {
    /**
     * Adds each of the given entries, keeping any already held.
     *
     * @param activities the entries to add
     */
    void addAll(List<Activity> activities);
}
