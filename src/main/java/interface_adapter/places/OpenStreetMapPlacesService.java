package interface_adapter.places;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import use_case.ports.ActivitySearchGateway;
import use_case.ports.DestinationGeocoder;
import use_case.ports.NamedPlaceSearch;
import use_case.ports.NearbyActivityDiscovery;
import use_case.ports.PlacesService;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.PlaceSearchException;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;

/** Local-first search coordinator; network details remain behind focused ports. */
public final class OpenStreetMapPlacesService
        implements PlacesService, ActivitySearchGateway, DestinationGeocoder {
    private final NamedPlaceSearch namedPlaces;
    private final NearbyActivityDiscovery nearby;
    private final DestinationGeocoder geocoder;
    private final Map<String, Map<String, Activity>> index = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Activity>> visibleIndex = new ConcurrentHashMap<>();

    public OpenStreetMapPlacesService() {
        final NominatimNamedPlaceSearch nominatim = new NominatimNamedPlaceSearch();
        this.namedPlaces = nominatim;
        this.nearby = new OverpassNearbyActivityDiscovery(nominatim);
        this.geocoder = nominatim;
    }

    public OpenStreetMapPlacesService(NamedPlaceSearch namedPlaces,
                                     NearbyActivityDiscovery nearby) {
        this.namedPlaces = namedPlaces;
        this.nearby = nearby;
        this.geocoder = namedPlaces instanceof DestinationGeocoder
                ? (DestinationGeocoder) namedPlaces : null;
    }

    @Override
    public entity.valueobjects.GeoPoint geocode(String destination) {
        if (geocoder == null) {
            throw new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                    "Destination geocoding is unavailable");
        }
        return geocoder.geocode(destination);
    }

    @Override
    public ActivitySearchResult search(ActivitySearchRequest request) {
        if (request.getDestination().isEmpty()) {
            return new ActivitySearchResult(
                    List.of(), SearchSource.LOCAL, false, SearchFailure.INVALID_DESTINATION);
        }
        final String destinationKey = normalize(request.getDestination());
        final List<Activity> local = filterAndRank(candidates(destinationKey), request);
        if (request.getQuery().isEmpty()) {
            return discoverNearby(request, destinationKey, local);
        }
        // An exact cached name is complete enough to answer immediately (e.g. Tim Hortons).
        if (hasExactName(local, request.getQuery())) {
            return new ActivitySearchResult(
                    limit(local, request.getLimit()), SearchSource.LOCAL, false, SearchFailure.NONE);
        }
        try {
            final List<Activity> remote = namedPlaces.find(request.getDestination(),
                    request.getQuery(), request.getLimit());
            add(destinationKey, remote);
            final List<Activity> merged = filterAndRank(candidates(destinationKey), request);
            if (merged.isEmpty()) {
                return new ActivitySearchResult(List.of(),
                        SearchSource.NOMINATIM, false, SearchFailure.NO_MATCH);
            }
            return new ActivitySearchResult(limit(merged, request.getLimit()),
                    local.isEmpty() ? SearchSource.NOMINATIM : SearchSource.LOCAL_AND_REMOTE,
                    false, SearchFailure.NONE);
        }
        catch (PlaceSearchException failure) {
            return new ActivitySearchResult(limit(local, request.getLimit()), SearchSource.LOCAL,
                    !local.isEmpty(), failure.getFailure());
        }
    }

    private ActivitySearchResult discoverNearby(ActivitySearchRequest request,
                                                String destinationKey,
                                                List<Activity> local) {
        try {
            final List<Activity> remote = nearby.around(request.getDestination(), request.getLimit());
            add(destinationKey, remote);
            final List<Activity> merged = filterAndRank(candidates(destinationKey), request);
            return new ActivitySearchResult(limit(merged, request.getLimit()),
                    local.isEmpty() ? SearchSource.OVERPASS : SearchSource.LOCAL_AND_REMOTE,
                    false, merged.isEmpty() ? SearchFailure.NO_MATCH : SearchFailure.NONE);
        }
        catch (PlaceSearchException failure) {
            return new ActivitySearchResult(limit(local, request.getLimit()), SearchSource.LOCAL,
                    !local.isEmpty(), failure.getFailure());
        }
    }

    @Override
    public List<Activity> search(String destination, String query) {
        return search(new ActivitySearchRequest(destination, query, null, null, 100))
                .getActivities();
    }

    @Override
    public List<Activity> searchInBounds(String destination, double south, double west,
                                         double north, double east, int maxResults) {
        final Map<String, Activity> viewport = visibleIndex(normalize(destination));
        // A box that lies fully inside the already-fetched whole-city discovery is answered from
        // that cache instead of issuing another Overpass request.
        final List<Activity> covered = nearby.cachedInBounds(
                destination, south, west, north, east);
        if (covered != null) {
            for (Activity activity : covered) {
                viewport.put(activity.getId(), activity);
            }
            return covered;
        }
        final List<Activity> activities = nearby.inBounds(south, west, north, east, maxResults);
        for (Activity activity : activities) {
            viewport.put(activity.getId(), activity);
        }
        return activities;
    }

    private Map<String, Activity> index(String destination) {
        return index.computeIfAbsent(destination, ignored -> new ConcurrentHashMap<>());
    }

    private Map<String, Activity> visibleIndex(String destination) {
        return visibleIndex.computeIfAbsent(destination, ignored -> new ConcurrentHashMap<>());
    }

    private void add(String destination, List<Activity> activities) {
        final Map<String, Activity> destinationIndex = index(destination);
        for (Activity activity : activities) {
            destinationIndex.put(activity.getId(), activity);
        }
    }

    private Iterable<Activity> candidates(String destination) {
        // Viewport places are recorded per destination so a trip that finishes loading after the
        // user has moved on can never leak its markers into the next itinerary's search results.
        final Map<String, Activity> merged = new LinkedHashMap<>(visibleIndex(destination));
        merged.putAll(index(destination));
        return merged.values();
    }

    private static List<Activity> filterAndRank(Iterable<Activity> source,
                                                ActivitySearchRequest request) {
        final List<Activity> matches = new ArrayList<>();
        final String query = normalize(request.getQuery());
        for (Activity activity : source) {
            if (request.getCategory() != null && activity.getCategory() != request.getCategory()) {
                continue;
            }
            if (request.getSetting() != null
                    && activity.getIndoorOutdoorType() != request.getSetting()) {
                continue;
            }
            if (!query.isEmpty() && score(activity, query) <= 0) {
                continue;
            }
            matches.add(activity);
        }
        matches.sort(Comparator.comparingDouble((Activity activity) -> score(activity, query))
                .reversed().thenComparing(Activity::getName));
        return matches;
    }

    private static double score(Activity activity, String query) {
        if (query.isEmpty()) {
            return metadataScore(activity);
        }
        final String name = normalize(activity.getName());
        final String address = normalize(activity.getLocation().getAddress());
        final String category = normalize(activity.getCategory().name().replace('_', ' '));
        if (name.equals(query)) {
            return 100 + metadataScore(activity);
        }
        if (name.startsWith(query)) {
            return 85 + metadataScore(activity);
        }
        if (name.contains(query)) {
            return 70 + metadataScore(activity);
        }
        final String[] tokens = query.split("\\s+");
        int found = 0;
        for (String token : tokens) {
            if (name.contains(token) || address.contains(token) || category.contains(token)) {
                found++;
            }
        }
        if (found == 0) {
            return 0;
        }
        return 45.0 * found / tokens.length + metadataScore(activity);
    }

    private static double metadataScore(Activity activity) {
        double score = 0;
        if (activity.getLocation() != null
                && activity.getLocation().getAddress() != null
                && !activity.getLocation().getAddress().equals(activity.getName())) {
            score += 5;
        }
        if (activity.getOpeningHoursText() != null) {
            score += 3;
        }
        return score;
    }

    private static boolean hasExactName(List<Activity> activities, String query) {
        final String normalized = normalize(query);
        for (Activity activity : activities) {
            if (normalize(activity.getName()).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static List<Activity> limit(List<Activity> activities, int limit) {
        return new ArrayList<>(activities.subList(0, Math.min(limit, activities.size())));
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
