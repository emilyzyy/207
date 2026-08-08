package closeai.infrastructure.ai;

import closeai.application.tripassistant.TripAssistantDecision;
import closeai.application.tripassistant.TripAssistantRequest;
import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OpenAiTripAssistantGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void postsGroundedStructuredRequestToResponsesApiAndParsesIds() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        AtomicReference<String> authorization = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("{\"status\":\"completed\",\"output\":[{"
                    + "\"type\":\"message\",\"content\":[{\"type\":\"output_text\","
                    + "\"text\":\"{\\\"intent\\\":\\\"RAIN\\\","
                    + "\\\"activity_ids\\\":[\\\"museum\\\"]}\"}]}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/v1/responses");
            OpenAiTripAssistantGateway gateway = new OpenAiTripAssistantGateway(
                    HttpClient.newHttpClient(), mapper, endpoint, "fake-test-key",
                    "gpt-test", Duration.ofSeconds(5));

            TripAssistantDecision decision = gateway.answer(request());

            assertEquals(TripAssistantDecision.Intent.RAIN, decision.getIntent());
            assertEquals(Collections.singletonList("museum"), decision.getActivityIds());
            assertEquals("Bearer fake-test-key", authorization.get());
            JsonNode root = mapper.readTree(requestBody.get());
            assertEquals("gpt-test", root.path("model").asText());
            assertFalse(root.path("store").asBoolean(true));
            assertEquals("json_schema",
                    root.path("text").path("format").path("type").asText());
            assertEquals("museum", root.path("text").path("format").path("schema")
                    .path("properties").path("activity_ids").path("items")
                    .path("enum").get(0).asText());
            JsonNode context = mapper.readTree(root.path("input").asText());
            assertEquals("Toronto", context.path("destination").asText());
            assertEquals("TRANSIT", context.path("transportation_mode").asText());
            assertTrue(context.has("bookmarked_activity_ids"));
            assertTrue(context.has("day_plan"));
            assertTrue(context.has("weather"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void proxyRequestDoesNotContainAnOpenAiAuthorizationHeader() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("{\"status\":\"completed\",\"output\":[{"
                    + "\"type\":\"message\",\"content\":[{\"type\":\"output_text\","
                    + "\"text\":\"{\\\"intent\\\":\\\"GENERAL\\\","
                    + "\\\"activity_ids\\\":[\\\"museum\\\"]}\"}]}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/v1/responses");
            OpenAiTripAssistantGateway gateway =
                    OpenAiTripAssistantGateway.viaProxy(endpoint, "gpt-5.4-mini");

            TripAssistantDecision decision = gateway.answer(request());

            assertEquals(TripAssistantDecision.Intent.GENERAL, decision.getIntent());
            assertNull(authorization.get());
        } finally {
            server.stop(0);
        }
    }

    private TripAssistantRequest request() {
        Activity museum = new Activity(
                "museum", "Actual Museum", ActivityCategory.MUSEUM,
                new Location(43.6, -79.3, "Museum address"), 4.8, 90,
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                IndoorOutdoorType.INDOOR, "Low");
        return new TripAssistantRequest(
                "Toronto", LocalDate.of(2026, 8, 20), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.TRANSIT,
                Collections.singletonList(museum),
                Collections.singleton("museum"), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                "What should I do if it rains?");
    }
}
