package closeai.infrastructure.mock;

import closeai.application.ports.DistanceService;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;

public final class MockDistanceService implements DistanceService {
    public int estimateTravelMinutes(Location from, Location to, TransportationMode mode) {
        double km = Math.max(0.5, from.calculateDistanceTo(to));
        double speed = mode == TransportationMode.DRIVING ? 24.0
                : mode == TransportationMode.TRANSIT ? 16.0 : 4.8;
        return Math.max(10, (int) Math.round(km / speed * 60.0 + (mode == TransportationMode.TRANSIT ? 6 : 2)));
    }
}
