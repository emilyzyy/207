package trippy.adapters.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

final class StaticTileLoader {
    static final int TILE_SIZE = 256;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private static final ConcurrentHashMap<String, CompletableFuture<double[]>> COORDINATES =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<BufferedImage>> TILES =
            new ConcurrentHashMap<>();
    private static long nextGeocodeAt;

    private StaticTileLoader() {}

    static double[] latLngForCity(String city) {
        switch (city.toLowerCase()) {
            case "toronto": return new double[]{43.6532, -79.3832};
            case "new york city": return new double[]{40.7128, -74.0060};
            case "montreal": return new double[]{45.5017, -73.5673};
            case "ottawa": return new double[]{45.4215, -75.6972};
            case "vancouver": return new double[]{49.2827, -123.1207};
            default: return null;
        }
    }

    /** Loads a map tile centered on the given city, geocoding unknown cities on the fly. */
    static CompletableFuture<BufferedImage> loadCityTile(String city, int zoom) {
        return cityCoords(city).thenCompose(coords ->
                coords == null ? CompletableFuture.completedFuture(null)
                        : loadTile(coords[0], coords[1], zoom));
    }

    static CompletableFuture<double[]> cityCoords(String city) {
        double[] known = latLngForCity(city);
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }
        String key = city == null ? "" : city.trim().toLowerCase();
        if (key.isEmpty()) return CompletableFuture.completedFuture(null);
        CompletableFuture<double[]> future = COORDINATES.computeIfAbsent(key,
                ignored -> CompletableFuture.supplyAsync(() -> geocodeCity(city)));
        future.thenAccept(coords -> {
            if (coords == null) COORDINATES.remove(key, future);
        });
        return future;
    }

    private static double[] geocodeCity(String city) {
        try {
            throttleGeocoding();
            String uri = "https://nominatim.openstreetmap.org/search?q="
                    + URLEncoder.encode(city, StandardCharsets.UTF_8)
                    + "&format=json&limit=1";
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .header("User-Agent", "Trippy-CSC207/1.0")
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JsonNode root = new ObjectMapper().readTree(response.body());
            if (!root.isArray() || root.isEmpty()) return null;
            JsonNode first = root.get(0);
            return new double[]{first.get("lat").asDouble(), first.get("lon").asDouble()};
        } catch (Exception ignored) {
            return null;
        }
    }

    private static synchronized void throttleGeocoding() throws InterruptedException {
        long wait = nextGeocodeAt - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        nextGeocodeAt = System.currentTimeMillis() + 1000L;
    }

    static CompletableFuture<BufferedImage> loadTile(double lat, double lng, int zoom) {
        String cacheKey = zoom + "|" + Math.round(lat * 10_000)
                + "|" + Math.round(lng * 10_000);
        CompletableFuture<BufferedImage> future = TILES.computeIfAbsent(cacheKey,
                ignored -> loadTileUncached(lat, lng, zoom));
        future.thenAccept(image -> {
            if (image == null) TILES.remove(cacheKey, future);
        });
        return future;
    }

    private static CompletableFuture<BufferedImage> loadTileUncached(
            double lat, double lng, int zoom) {
        int maxTiles = 1 << zoom;
        double lngTile = (lng + 180.0) / 360.0 * maxTiles;
        int tx = (int) Math.floor(lngTile);
        double latRad = Math.toRadians(lat);
        double latTile = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * maxTiles;
        int ty = (int) Math.floor(latTile);
        String url = "https://tile.openstreetmap.org/" + zoom + "/" + tx + "/" + ty + ".png";
        return CompletableFuture.supplyAsync(() -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                            .header("User-Agent", "Trippy-CSC207/1.0 (github.com/emilyzyy/207)")
                            .GET().timeout(Duration.ofSeconds(8)).build();
                    HttpResponse<InputStream> res = HTTP.send(req,
                            HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream body = res.body()) {
                        if (res.statusCode() == 200) return ImageIO.read(body);
                    }
                } catch (Exception ignored) {
                    // One retry handles transient tile-server/network failures.
                }
            }
            return null;
        });
    }
}
