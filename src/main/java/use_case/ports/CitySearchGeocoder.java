package use_case.ports;

import java.util.List;

/** Autocompletes city names into geocoded candidates for the trip-destination picker. */
public interface CitySearchGeocoder {
    /**
     * Searches for cities matching the given query.
     *
     * @param query the partial city name
     * @param limit maximum number of candidates to return
     * @return geocoded city candidates, never null
     */
    List<CityCandidate> search(String query, int limit);
}
