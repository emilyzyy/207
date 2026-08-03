package closeai.adapters.views;

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
import javax.imageio.ImageIO;

final class StaticTileLoader {
    static final int TILE_SIZE = 256;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

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
        return CompletableFuture.supplyAsync(() -> geocodeCity(city));
    }

    private static double[] geocodeCity(String city) {
        try {
            String uri = "https://nominatim.openstreetmap.org/search?q="
                    + URLEncoder.encode(city, StandardCharsets.UTF_8)
                    + "&format=json&limit=1";
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .header("User-Agent", "CloseAI-CSC207/1.0")
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

    static CompletableFuture<BufferedImage> loadTile(double lat, double lng, int zoom) {
        int maxTiles = 1 << zoom;
        double lngTile = (lng + 180.0) / 360.0 * maxTiles;
        int tx = (int) Math.floor(lngTile);
        double latRad = Math.toRadians(lat);
        double latTile = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * maxTiles;
        int ty = (int) Math.floor(latTile);
        String url = "https://tile.openstreetmap.org/" + zoom + "/" + tx + "/" + ty + ".png";
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "CloseAI-CSC207/1.0")
                        .GET().timeout(Duration.ofSeconds(8)).build();
                HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() == 200) {
                    BufferedImage img = ImageIO.read(res.body());
                    res.body().close();
                    return img;
                }
                res.body().close();
            } catch (Exception ignored) {}
            return null;
        });
    }
}
