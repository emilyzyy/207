package interface_adapter.routing;

import java.time.LocalDateTime;

import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import use_case.ports.DistanceService;

/**
 * Resolves a request for {@link TransportationMode#FASTEST} into a real answer.
 *
 * <p>Asks the delegate for every specific mode and keeps the quickest, so a traveller who
 * does not want to commit to one still gets a plan. A specific mode is passed straight
 * through untouched — the point is to serve a choice not to choose, not to override the
 * choice of someone who made one.</p>
 *
 * <p>A null mode is treated as {@code FASTEST} for callers that have no opinion.</p>
 */
public final class FastestModeDistanceService implements DistanceService {
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
        if (mode != null && mode.isSpecific()) {
            return delegate.estimateTravelMinutes(from, to, mode, departure);
        }
        int best = Integer.MAX_VALUE;
        for (TransportationMode candidate : TransportationMode.specificModes()) {
            best = Math.min(best,
                    delegate.estimateTravelMinutes(from, to, candidate, departure));
        }
        return best;
    }
}
