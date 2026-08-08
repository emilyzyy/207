package trippy.infrastructure.routing;

import trippy.application.ports.DistanceService;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FastestModeDistanceServiceTest {
    private static final Location HERE = new Location(43.65, -79.38, "Toronto");

    @Test
    void returnsFastestAcrossAllSupportedModesWhenAskedForFastest() {
        RecordingDistanceService delegate = new RecordingDistanceService();
        delegate.put(TransportationMode.WALKING, 60);
        delegate.put(TransportationMode.DRIVING, 20);
        delegate.put(TransportationMode.TRANSIT, 45);
        FastestModeDistanceService service = new FastestModeDistanceService(delegate);

        int minutes = service.estimateTravelMinutes(HERE, HERE,
                TransportationMode.FASTEST, LocalDateTime.now());

        assertEquals(20, minutes);
        assertEquals(List.of(
                TransportationMode.WALKING,
                TransportationMode.DRIVING,
                TransportationMode.TRANSIT), delegate.modes);
    }

    /**
     * The behaviour this class used to have was to ignore the requested mode entirely.
     * That made "fastest" an override rather than an option, and it could quietly plan a
     * day around a car the traveller does not have. A specific mode is now honoured.
     */
    @Test
    void aSpecificModeIsPassedStraightThroughAndCostsOneCall() {
        RecordingDistanceService delegate = new RecordingDistanceService();
        delegate.put(TransportationMode.WALKING, 80);
        delegate.put(TransportationMode.DRIVING, 35);
        delegate.put(TransportationMode.TRANSIT, 40);
        FastestModeDistanceService service = new FastestModeDistanceService(delegate);

        int minutes = service.estimateTravelMinutes(HERE, HERE,
                TransportationMode.WALKING, LocalDateTime.now());

        assertEquals(80, minutes, "asking to walk must cost what walking costs");
        assertEquals(List.of(TransportationMode.WALKING), delegate.modes,
                "one leg, one route request -- not three");
    }

    @Test
    void aNullModeIsTreatedAsFastestForCallersWithNoOpinion() {
        RecordingDistanceService delegate = new RecordingDistanceService();
        delegate.put(TransportationMode.WALKING, 80);
        delegate.put(TransportationMode.DRIVING, 35);
        delegate.put(TransportationMode.TRANSIT, 40);

        assertEquals(35, new FastestModeDistanceService(delegate)
                .estimateTravelMinutes(HERE, HERE, null, LocalDateTime.now()));
    }

    private static final class RecordingDistanceService implements DistanceService {
        private final List<TransportationMode> modes = new ArrayList<>();
        private final Map<TransportationMode, Integer> minutes = new HashMap<>();

        void put(TransportationMode mode, int value) {
            minutes.put(mode, value);
        }

        @Override
        public int estimateTravelMinutes(Location from, Location to, TransportationMode mode,
                                         LocalDateTime departure) {
            modes.add(mode);
            return minutes.getOrDefault(mode, Integer.MAX_VALUE);
        }
    }
}
