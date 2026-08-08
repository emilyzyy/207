package trippy.application.ports;

import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import java.time.LocalDateTime;

public interface DistanceService {
    int estimateTravelMinutes(Location from, Location to, TransportationMode mode, LocalDateTime departure);
}
