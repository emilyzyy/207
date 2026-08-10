package interface_adapter.mock;

import use_case.ports.DistanceService;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import java.time.LocalDateTime;

public final class MockDistanceService implements DistanceService {
    public int estimateTravelMinutes(Location from, Location to, TransportationMode mode, LocalDateTime departure) {
        double km = Math.max(0.5, from.calculateDistanceTo(to));
        double speed = mode == TransportationMode.DRIVING ? 24.0
                : mode == TransportationMode.TRANSIT ? 16.0 : 4.8;
        return Math.max(10, (int) Math.round(km / speed * 60.0 + (mode == TransportationMode.TRANSIT ? 6 : 2)));
    }
}
