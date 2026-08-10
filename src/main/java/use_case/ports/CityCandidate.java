package use_case.ports;

/** A geocoded city suggestion for the trip-destination picker. */
public final class CityCandidate {
    private final String name;
    private final String region;
    private final String country;
    private final double latitude;
    private final double longitude;

    /**
     * Creates a city candidate.
     *
     * @param name the city name
     * @param region the region or province, or empty when unknown
     * @param country the country, or empty when unknown
     * @param latitude the geocoded latitude
     * @param longitude the geocoded longitude
     */
    public CityCandidate(String name, String region, String country,
                         double latitude, double longitude) {
        this.name = name;
        this.region = region;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public String getCountry() {
        return country;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
