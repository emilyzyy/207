package closeai.application.ports;

import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDateTime;

public interface DistanceService {
    int estimateTravelMinutes(Location from, Location to, TransportationMode mode, LocalDateTime departure);
}
