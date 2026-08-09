package use_case.ports;

import use_case.search.GeoPoint;

public interface DestinationGeocoder {
    GeoPoint geocode(String destination);
}
