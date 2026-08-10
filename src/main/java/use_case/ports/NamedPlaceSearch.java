package use_case.ports;

import java.util.List;

import entity.entities.Activity;

/** Finds specifically named places; implemented by Nominatim at the infrastructure edge. */
public interface NamedPlaceSearch {
    /**
     * Performs the f in d operation.
     * @param query the q ue ry value
     * @param limit the l im it value
     * @param destination the d es ti na ti on value
     * @return the result of the operation
     */
    List<Activity> find(String destination, String query, int limit);
}
