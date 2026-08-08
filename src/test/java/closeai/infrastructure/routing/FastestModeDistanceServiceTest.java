package closeai.infrastructure.routing;

import closeai.application.ports.DistanceService;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
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
    void returnsFastestAcrossAllSupportedModes() {
        RecordingDistanceService delegate = new RecordingDistanceService();
        delegate.put(TransportationMode.WALKING, 60);
        delegate.put(TransportationMode.DRIVING, 20);
        delegate.put(TransportationMode.TRANSIT, 45);
        FastestModeDistanceService service = new FastestModeDistanceService(delegate);

        int minutes = service.estimateTravelMinutes(HERE, HERE,
                TransportationMode.WALKING, LocalDateTime.now());

        assertEquals(20, minutes);
        assertEquals(List.of(
                TransportationMode.WALKING,
                TransportationMode.DRIVING,
                TransportationMode.TRANSIT), delegate.modes);
    }

    @Test
    void ignoresRequestedModeWhenChoosingFastest() {
        RecordingDistanceService delegate = new RecordingDistanceService();
        delegate.put(TransportationMode.WALKING, 80);
        delegate.put(TransportationMode.DRIVING, 35);
        delegate.put(TransportationMode.TRANSIT, 40);
        FastestModeDistanceService service = new FastestModeDistanceService(delegate);

        int minutes = service.estimateTravelMinutes(HERE, HERE,
                TransportationMode.DRIVING, LocalDateTime.now());

        assertEquals(35, minutes);
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
