package closeai.domain.valueobjects;

public final class Location {
    private final double latitude;
    private final double longitude;
    private final String address;

    public Location(double latitude, double longitude, String address) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getAddress() { return address; }

    public double calculateDistanceTo(Location other) {
        final double earthRadiusKm = 6371.0;
        double lat = Math.toRadians(other.latitude - latitude);
        double lon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
