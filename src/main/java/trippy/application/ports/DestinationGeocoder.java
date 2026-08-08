package trippy.application.ports;

import trippy.application.search.GeoPoint;

public interface DestinationGeocoder {
    GeoPoint geocode(String destination);
}
