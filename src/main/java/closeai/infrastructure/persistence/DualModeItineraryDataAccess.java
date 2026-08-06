package closeai.infrastructure.persistence;

import closeai.application.ports.AuthService;
import closeai.application.ports.ItineraryDataAccessInterface;
import closeai.application.ports.TripRepository;
import closeai.domain.entities.Trip;
import closeai.infrastructure.supabase.SupabaseItineraryDataAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Uses local in-memory storage while signed out; Supabase while signed in.
 * Creating/editing always works; cloud sync happens only with a session.
 */
public final class DualModeItineraryDataAccess
        implements TripRepository, ItineraryDataAccessInterface {
    private final InMemoryItineraryDataAccessObject local;
    private final SupabaseItineraryDataAccess remote;
    private final AuthService auth;

    public DualModeItineraryDataAccess(
            InMemoryItineraryDataAccessObject local,
            SupabaseItineraryDataAccess remote,
            AuthService auth) {
        if (local == null || remote == null || auth == null) {
            throw new IllegalArgumentException("Dual-mode persistence dependencies are required");
        }
        this.local = local;
        this.remote = remote;
        this.auth = auth;
    }

    public void clearLocal() {
        local.clear();
    }

    /** After sign-in, push a local trip (if present) up to the account. */
    public void syncTripToCloud(String tripId) {
        if (!cloud()) {
            return;
        }
        Optional<Trip> localTrip = local.findById(tripId);
        if (localTrip.isPresent()) {
            remote.save(localTrip.get());
            return;
        }
        Optional<Trip> already = remote.findById(tripId);
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
            Trip saved = remote.saveItinerary(itinerary);
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
            Optional<Trip> remoteTrip = remote.loadItinerary(itineraryId);
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
            List<Trip> remoteTrips = remote.findAll();
            for (Trip trip : remoteTrips) {
                local.save(trip);
            }
            return remoteTrips;
        }
        return new ArrayList<Trip>(local.findAll());
    }
}
