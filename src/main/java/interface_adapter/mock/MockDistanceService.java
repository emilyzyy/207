package interface_adapter.mock;

import java.time.LocalDateTime;

import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import use_case.ports.DistanceService;

public final class MockDistanceService implements DistanceService {
    /**
     * Performs the e st im at et ra ve lm in ut es operation.
     * @param departure the d ep ar tu re value
     * @param mode the m od e value
     * @param to the t o value
     * @param from the f ro m value
     * @return the result of the operation
     */
    public int estimateTravelMinutes(Location from, Location to, TransportationMode mode, LocalDateTime departure) {
        final double km = Math.max(0.5, from.calculateDistanceTo(to));
        final double speed = mode == TransportationMode.DRIVING ? 24.0
                : mode == TransportationMode.TRANSIT ? 16.0 : 4.8;
        return Math.max(10, (int) Math.round(km / speed * 60.0 + (mode == TransportationMode.TRANSIT ? 6 : 2)));
    }
}
