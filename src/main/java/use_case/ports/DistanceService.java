package use_case.ports;

import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import java.time.LocalDateTime;

public interface DistanceService {
    int estimateTravelMinutes(Location from, Location to, TransportationMode mode, LocalDateTime departure);
}
