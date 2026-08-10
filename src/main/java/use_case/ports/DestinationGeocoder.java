package use_case.ports;

import entity.valueobjects.GeoPoint;

public interface DestinationGeocoder {
    /**
     * Performs the g eo co de operation.
     * @param destination the d es ti na ti on value
     * @return the result of the operation
     */
    GeoPoint geocode(String destination);
}
