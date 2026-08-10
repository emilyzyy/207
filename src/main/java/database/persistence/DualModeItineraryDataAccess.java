package database.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import entity.entities.Trip;
import use_case.ports.AuthService;
import use_case.ports.ItineraryDataAccessInterface;
import use_case.ports.TripRepository;

/**
 * Uses local in-memory storage while signed out; remote cloud storage while signed in.
 * Creating/editing always works; cloud sync happens only with a session.
 *
 * <p>The remote must implement both {@link TripRepository} and
 * {@link ItineraryDataAccessInterface} (as {@code SupabaseItineraryDataAccess} does).
 */
public final class DualModeItineraryDataAccess
        implements TripRepository, ItineraryDataAccessInterface {
    private final InMemoryItineraryDataAccessObject local;
    private final TripRepository remote;
    private final ItineraryDataAccessInterface remoteItinerary;
    private final AuthService auth;

    public DualModeItineraryDataAccess(
            InMemoryItineraryDataAccessObject local,
            TripRepository remote,
            AuthService auth) {
        if (local == null || remote == null || auth == null) {
            throw new IllegalArgumentException("Dual-mode persistence dependencies are required");
        }
        if (!(remote instanceof ItineraryDataAccessInterface)) {
            throw new IllegalArgumentException(
                    "Remote must implement ItineraryDataAccessInterface");
        }
        this.local = local;
        this.remote = remote;
        this.remoteItinerary = (ItineraryDataAccessInterface) remote;
        this.auth = auth;
    }

    /** Performs the c le ar lo ca l operation. */
    public void clearLocal() {
        local.clear();
    }
    /**
     * After sign-in, push a local trip (if present) up to the account.
     * @param tripId the t ri pi d value
     */

    public void syncTripToCloud(String tripId) {
        if (!cloud()) {
            return;
        }
        final Optional<Trip> localTrip = local.findById(tripId);
        if (localTrip.isPresent()) {
            remote.save(localTrip.get());
            return;
        }
        final Optional<Trip> already = remote.findById(tripId);
        if (!already.isPresent()) {
            // nothing to sync
        }
    }

    private boolean cloud() {
        return auth.currentSession().isPresent();
    }

    @Override
    public Trip save(Trip trip) {
        return saveItinerary(trip);
    }

    @Override
    public Trip saveItinerary(Trip itinerary) {
        if (cloud()) {
            final Trip saved = remoteItinerary.saveItinerary(itinerary);
            local.save(saved);
            return saved;
        }
        return local.saveItinerary(itinerary);
    }

    @Override
    public Optional<Trip> findById(String id) {
        return loadItinerary(id);
    }

    @Override
    public Optional<Trip> loadItinerary(String itineraryId) {
        if (cloud()) {
            final Optional<Trip> remoteTrip = remoteItinerary.loadItinerary(itineraryId);
            if (remoteTrip.isPresent()) {
                local.save(remoteTrip.get());
                return remoteTrip;
            }
        }
        return local.loadItinerary(itineraryId);
    }

    @Override
    public boolean existsById(String itineraryId) {
        return loadItinerary(itineraryId).isPresent();
    }

    @Override
    public List<Trip> findAll() {
        if (cloud()) {
            final List<Trip> remoteTrips = remote.findAll();
            for (Trip trip : remoteTrips) {
                local.save(trip);
            }
            return remoteTrips;
        }
        return new ArrayList<Trip>(local.findAll());
    }

    @Override
    public boolean deleteById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        if (cloud()) {
            remote.deleteById(id);
        }
        return local.deleteById(id) || cloud();
    }
}
