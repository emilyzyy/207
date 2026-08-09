package interface_adapter.places;

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
import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Local-first search coordinator; network details remain behind focused ports. */
public final class OpenStreetMapPlacesService
        implements PlacesService, ActivitySearchGateway, DestinationGeocoder {
    private final NamedPlaceSearch namedPlaces;
    private final NearbyActivityDiscovery nearby;
    private final DestinationGeocoder geocoder;
    private final Map<String, Map<String, Activity>> index = new ConcurrentHashMap<>();
    private final Map<String, Activity> visibleIndex = new ConcurrentHashMap<>();
    private volatile String activeDestination = "";

    public OpenStreetMapPlacesService() {
        NominatimNamedPlaceSearch nominatim = new NominatimNamedPlaceSearch();
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
    public use_case.search.GeoPoint geocode(String destination) {
        if (geocoder == null) {
            throw new PlaceSearchException(SearchFailure.SERVICE_UNAVAILABLE,
                    "Destination geocoding is unavailable");
        }
        return geocoder.geocode(destination);
    }

    @Override
    public ActivitySearchResult search(ActivitySearchRequest request) {
        if (request.getDestination().isEmpty()) return new ActivitySearchResult(
                List.of(), SearchSource.LOCAL, false, SearchFailure.INVALID_DESTINATION);
        String destinationKey = normalize(request.getDestination());
        selectDestination(destinationKey);
        List<Activity> local = filterAndRank(candidates(destinationKey), request);
        if (request.getQuery().isEmpty()) {
            return discoverNearby(request, destinationKey, local);
        }
        // An exact cached name is complete enough to answer immediately (e.g. Tim Hortons).
        if (hasExactName(local, request.getQuery())) return new ActivitySearchResult(
                limit(local, request.getLimit()), SearchSource.LOCAL, false, SearchFailure.NONE);
        try {
            List<Activity> remote = namedPlaces.find(request.getDestination(),
                    request.getQuery(), request.getLimit());
            add(destinationKey, remote);
            List<Activity> merged = filterAndRank(candidates(destinationKey), request);
            if (merged.isEmpty()) return new ActivitySearchResult(List.of(),
                    SearchSource.NOMINATIM, false, SearchFailure.NO_MATCH);
            return new ActivitySearchResult(limit(merged, request.getLimit()),
                    local.isEmpty() ? SearchSource.NOMINATIM : SearchSource.LOCAL_AND_REMOTE,
                    false, SearchFailure.NONE);
        } catch (PlaceSearchException failure) {
            return new ActivitySearchResult(limit(local, request.getLimit()), SearchSource.LOCAL,
                    !local.isEmpty(), failure.getFailure());
        }
    }

    private ActivitySearchResult discoverNearby(ActivitySearchRequest request,
                                                String destinationKey,
                                                List<Activity> local) {
        try {
            List<Activity> remote = nearby.around(request.getDestination(), request.getLimit());
            add(destinationKey, remote);
            List<Activity> merged = filterAndRank(candidates(destinationKey), request);
            return new ActivitySearchResult(limit(merged, request.getLimit()),
                    local.isEmpty() ? SearchSource.OVERPASS : SearchSource.LOCAL_AND_REMOTE,
                    false, merged.isEmpty() ? SearchFailure.NO_MATCH : SearchFailure.NONE);
        } catch (PlaceSearchException failure) {
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
    public List<Activity> searchInBounds(double south, double west, double north, double east,
                                         int maxResults) {
        List<Activity> activities = nearby.inBounds(south, west, north, east, maxResults);
        for (Activity activity : activities) visibleIndex.put(activity.getId(), activity);
        return activities;
    }

    private Map<String, Activity> index(String destination) {
        return index.computeIfAbsent(destination, ignored -> new ConcurrentHashMap<>());
    }

    private synchronized void selectDestination(String destination) {
        if (destination.equals(activeDestination)) return;
        // A map may finish its first viewport load just before the first text search.
        // Preserve that initial viewport, but clear it when changing between known trips.
        if (!activeDestination.isEmpty()) visibleIndex.clear();
        activeDestination = destination;
    }

    private void add(String destination, List<Activity> activities) {
        Map<String, Activity> destinationIndex = index(destination);
        for (Activity activity : activities) destinationIndex.put(activity.getId(), activity);
    }

    private Iterable<Activity> candidates(String destination) {
        Map<String, Activity> merged = new LinkedHashMap<>(visibleIndex);
        merged.putAll(index(destination));
        return merged.values();
    }

    private static List<Activity> filterAndRank(Iterable<Activity> source,
                                                ActivitySearchRequest request) {
        List<Activity> matches = new ArrayList<>();
        String query = normalize(request.getQuery());
        for (Activity activity : source) {
            if (request.getCategory() != null && activity.getCategory() != request.getCategory())
                continue;
            if (request.getSetting() != null
                    && activity.getIndoorOutdoorType() != request.getSetting()) continue;
            if (!query.isEmpty() && score(activity, query) <= 0) continue;
            matches.add(activity);
        }
        matches.sort(Comparator.comparingDouble((Activity activity) -> score(activity, query))
                .reversed().thenComparing(Activity::getName));
        return matches;
    }

    private static double score(Activity activity, String query) {
        if (query.isEmpty()) return metadataScore(activity);
        String name = normalize(activity.getName());
        String address = normalize(activity.getLocation().getAddress());
        String category = normalize(activity.getCategory().name().replace('_', ' '));
        if (name.equals(query)) return 100 + metadataScore(activity);
        if (name.startsWith(query)) return 85 + metadataScore(activity);
        if (name.contains(query)) return 70 + metadataScore(activity);
        String[] tokens = query.split("\\s+");
        int found = 0;
        for (String token : tokens) {
            if (name.contains(token) || address.contains(token) || category.contains(token)) found++;
        }
        if (found == 0) return 0;
        return 45.0 * found / tokens.length + metadataScore(activity);
    }

    private static double metadataScore(Activity activity) {
        double score = 0;
        if (activity.getLocation() != null
                && activity.getLocation().getAddress() != null
                && !activity.getLocation().getAddress().equals(activity.getName())) score += 5;
        if (activity.getOpeningHoursText() != null) score += 3;
        return score;
    }

    private static boolean hasExactName(List<Activity> activities, String query) {
        String normalized = normalize(query);
        for (Activity activity : activities)
            if (normalize(activity.getName()).equals(normalized)) return true;
        return false;
    }

    private static List<Activity> limit(List<Activity> activities, int limit) {
        return new ArrayList<>(activities.subList(0, Math.min(limit, activities.size())));
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
