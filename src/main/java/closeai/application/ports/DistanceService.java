package closeai.application.ports;

import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;

public interface DistanceService {
    int estimateTravelMinutes(Location from, Location to, TransportationMode mode);
}
