package closeai.infrastructure.routing;

import closeai.application.ports.DistanceService;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDateTime;

/**
 * Decorator that answers travel time with the fastest of the three supported modes,
 * so scheduling never depends on a user-picked transportation choice.
 */
public final class FastestModeDistanceService implements DistanceService {
    private static final TransportationMode[] MODES = {
        TransportationMode.WALKING,
        TransportationMode.DRIVING,
        TransportationMode.TRANSIT
    };

    private final DistanceService delegate;

    public FastestModeDistanceService(DistanceService delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Distance service is required");
        }
        this.delegate = delegate;
    }

    @Override
    public int estimateTravelMinutes(Location from, Location to,
                                     TransportationMode mode, LocalDateTime departure) {
        int best = Integer.MAX_VALUE;
        for (TransportationMode candidate : MODES) {
            best = Math.min(best,
                    delegate.estimateTravelMinutes(from, to, candidate, departure));
        }
        return best;
    }
}
