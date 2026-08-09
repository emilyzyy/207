package use_case.ports;

import entity.valueobjects.GeoPoint;

public interface DestinationGeocoder {
    GeoPoint geocode(String destination);
}
