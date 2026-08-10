package use_case.ports;

import java.time.LocalDateTime;

import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;

public interface DistanceService {
    /**
     * Performs the e st im at et ra ve lm in ut es operation.
     * @param departure the d ep ar tu re value
     * @param mode the m od e value
     * @param to the t o value
     * @param from the f ro m value
     * @return the result of the operation
     */
    int estimateTravelMinutes(Location from, Location to, TransportationMode mode, LocalDateTime departure);
}
