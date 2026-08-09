package use_case.search;

public final class GeoPoint {
    private final double latitude;
    private final double longitude;
    private final int discoveryRadiusMeters;

    public GeoPoint(double latitude, double longitude) {
        this(latitude, longitude, 1500);
    }

    public GeoPoint(double latitude, double longitude, int discoveryRadiusMeters) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.discoveryRadiusMeters = Math.max(1500, Math.min(5000, discoveryRadiusMeters));
    }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getDiscoveryRadiusMeters() { return discoveryRadiusMeters; }
}
