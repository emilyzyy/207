package interface_adapter.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public final class StaticFileHandler implements HttpHandler {
    private final Path root;

    public StaticFileHandler(String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    /**
     * Performs the h an dl e operation.
     * @param exchange the e xc ha ng e value
     * @throws IOException if the operation cannot be completed
     */
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }
        Path file = root.resolve(requestPath.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.exists(file) || Files.isDirectory(file)) {
            file = root.resolve("index.html");
        }
        final byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file));
        exchange.sendResponseHeaders(200, bytes.length);
        final OutputStream output = exchange.getResponseBody();
        output.write(bytes);
        output.close();
    }

    private String contentType(Path file) {
        final String name = file.toString();
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        return "text/html; charset=utf-8";
    }
}
