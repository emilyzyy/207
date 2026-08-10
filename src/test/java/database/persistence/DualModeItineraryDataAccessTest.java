package database.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import entity.entities.Trip;
import entity.valueobjects.TransportationMode;
import use_case.ports.AuthService;
import use_case.ports.AuthSession;
import use_case.ports.ItineraryDataAccessInterface;
import use_case.ports.TripRepository;

/**
 * Dual-mode routing only: signed-out uses local memory; signed-in uses the remote port.
 * Does not exercise Supabase HTTP adapters.
 */
final class DualModeItineraryDataAccessTest {

    private InMemoryItineraryDataAccessObject local;
    private FakeRemote remote;
    private FakeAuth auth;
    private DualModeItineraryDataAccess dual;

    @BeforeEach
    void setUp() {
        local = new InMemoryItineraryDataAccessObject();
        remote = new FakeRemote();
        auth = new FakeAuth();
        dual = new DualModeItineraryDataAccess(local, remote, auth);
    }

    @Test
    void rejectsMissingDependencies() {
        assertThrows(IllegalArgumentException.class,
                () -> new DualModeItineraryDataAccess(null, remote, auth));
        assertThrows(IllegalArgumentException.class,
                () -> new DualModeItineraryDataAccess(local, null, auth));
        assertThrows(IllegalArgumentException.class,
                () -> new DualModeItineraryDataAccess(local, remote, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DualModeItineraryDataAccess(local, new TripRepositoryOnly(), auth));
    }

    @Test
    void signedOutSaveAndLoadUseLocalOnly() {
        final Trip trip = sampleTrip("local-1");
        dual.save(trip);

        assertEquals(0, remote.saveCount.get());
        assertEquals(0, remote.saveItineraryCount.get());
        assertTrue(remote.store.isEmpty());
        assertTrue(local.findById("local-1").isPresent());
        assertEquals("Toronto", dual.findById("local-1").orElseThrow().getDestination());
    }

    @Test
    void signedOutFindAllDoesNotTouchRemote() {
        dual.save(sampleTrip("a"));
        dual.save(sampleTrip("b"));

        final List<Trip> all = dual.findAll();
        assertEquals(2, all.size());
        assertEquals(0, remote.findAllCount.get());
    }

    @Test
    void signedInSaveWritesRemoteAndMirrorsLocal() {
        auth.signIn();
        final Trip trip = sampleTrip("cloud-1");
        dual.save(trip);

        assertEquals(1, remote.saveItineraryCount.get());
        assertTrue(remote.store.containsKey("cloud-1"));
        assertTrue(local.findById("cloud-1").isPresent());
    }

    @Test
    void signedInLoadPrefersRemoteAndCachesLocally() {
        auth.signIn();
        remote.store.put("cloud-2", sampleTrip("cloud-2"));
        local.clear();

        final Optional<Trip> loaded = dual.loadItinerary("cloud-2");
        assertTrue(loaded.isPresent());
        assertEquals(1, remote.loadCount.get());
        assertTrue(local.findById("cloud-2").isPresent());
    }

    @Test
    void signedInFindAllMirrorsRemoteIntoLocal() {
        auth.signIn();
        remote.store.put("r1", sampleTrip("r1"));
        remote.store.put("r2", sampleTrip("r2"));

        assertEquals(2, dual.findAll().size());
        assertTrue(local.findById("r1").isPresent());
        assertTrue(local.findById("r2").isPresent());
    }

    @Test
    void syncTripToCloudNoopsWhenSignedOut() {
        local.save(sampleTrip("sync-me"));
        dual.syncTripToCloud("sync-me");
        assertTrue(remote.store.isEmpty());
        assertEquals(0, remote.saveCount.get());
    }

    @Test
    void syncTripToCloudPushesLocalTripWhenSignedIn() {
        local.save(sampleTrip("sync-me"));
        auth.signIn();
        dual.syncTripToCloud("sync-me");
        assertTrue(remote.store.containsKey("sync-me"));
        assertEquals(1, remote.saveCount.get());
    }

    @Test
    void signedInDeleteRemovesFromRemoteAndLocal() {
        auth.signIn();
        dual.save(sampleTrip("gone"));
        assertTrue(dual.deleteById("gone"));
        assertFalse(remote.store.containsKey("gone"));
        assertFalse(local.findById("gone").isPresent());
    }

    private static Trip sampleTrip(String id) {
        return new Trip(id, "Toronto", LocalDate.of(2026, 8, 9),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
    }

    private static final class FakeAuth implements AuthService {
        private AuthSession session;

        void signIn() {
            session = new AuthSession("user-1", "token-1", "user@example.com");
        }

        @Override
        public AuthSession signUp(String email, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthSession signIn(String email, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthSession updateCredentials(String email, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void signOut() {
            session = null;
        }

        @Override
        public Optional<AuthSession> currentSession() {
            return Optional.ofNullable(session);
        }
    }

    /** In-memory stand-in for the cloud adapter; records how DualMode routes calls. */
    private static final class FakeRemote implements TripRepository, ItineraryDataAccessInterface {
        private final Map<String, Trip> store = new ConcurrentHashMap<String, Trip>();
        private final AtomicInteger saveCount = new AtomicInteger();
        private final AtomicInteger saveItineraryCount = new AtomicInteger();
        private final AtomicInteger loadCount = new AtomicInteger();
        private final AtomicInteger findAllCount = new AtomicInteger();

        @Override
        public Trip save(Trip trip) {
            saveCount.incrementAndGet();
            store.put(trip.getId(), trip);
            return trip;
        }

        @Override
        public Trip saveItinerary(Trip itinerary) {
            saveItineraryCount.incrementAndGet();
            return save(itinerary);
        }

        @Override
        public Optional<Trip> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Trip> loadItinerary(String itineraryId) {
            loadCount.incrementAndGet();
            return findById(itineraryId);
        }

        @Override
        public boolean existsById(String itineraryId) {
            return store.containsKey(itineraryId);
        }

        @Override
        public List<Trip> findAll() {
            findAllCount.incrementAndGet();
            return new ArrayList<Trip>(store.values());
        }

        @Override
        public boolean deleteById(String id) {
            return store.remove(id) != null;
        }
    }

    /** Implements TripRepository only — used to assert DualMode rejects a narrow remote. */
    private static final class TripRepositoryOnly implements TripRepository {
        @Override
        public Trip save(Trip trip) {
            return trip;
        }

        @Override
        public Optional<Trip> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<Trip> findAll() {
            return new ArrayList<Trip>();
        }
    }
}
