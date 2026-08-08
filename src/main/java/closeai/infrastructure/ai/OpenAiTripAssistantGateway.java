package closeai.infrastructure.ai;

import closeai.application.ports.TripAssistantGateway;
import closeai.application.tripassistant.TripAssistantDecision;
import closeai.application.tripassistant.TripAssistantMessage;
import closeai.application.tripassistant.TripAssistantRequest;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.WeatherWarning;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Live OpenAI Responses API implementation with schema-constrained activity IDs. */
public final class OpenAiTripAssistantGateway implements TripAssistantGateway {
    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.openai.com/v1/responses");
    private static final String INSTRUCTIONS = "Role: You are George, the travel assistant "
            + "inside CloseAI. Goal: choose up to three suitable activities for the user's "
            + "current trip. Use every supplied trip field as evidence. Constraints: select "
            + "only activity_id values from available_activities; never create a place, name, "
            + "or ID; use current bookmarks, Day Plan, weather, hours, duration, date, and "
            + "transportation mode. For a why question, reuse the most recent grounded activity "
            + "IDs in history when appropriate. Return only the requested structured data.";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public OpenAiTripAssistantGateway(String apiKey, String model) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(), DEFAULT_ENDPOINT, apiKey, model, Duration.ofSeconds(30));
    }

    public OpenAiTripAssistantGateway(
            HttpClient client, ObjectMapper mapper, URI endpoint, String apiKey,
            String model, Duration timeout) {
        if (client == null || mapper == null || endpoint == null || isBlank(apiKey)
                || isBlank(model) || timeout == null) {
            throw new IllegalArgumentException("OpenAI client configuration is required");
        }
        this.client = client;
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public TripAssistantDecision answer(TripAssistantRequest request) {
        if (request.getActivities().isEmpty()) {
            return new TripAssistantDecision(
                    TripAssistantDecision.Intent.GENERAL, Collections.<String>emptyList());
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson(request)))
                    .build();
            HttpResponse<String> response = client.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "OpenAI request failed with HTTP " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAI request could not be completed", exception);
        }
    }

    private String requestJson(TripAssistantRequest request) throws JsonProcessingException {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("model", model);
        root.put("instructions", INSTRUCTIONS);
        root.put("input", mapper.writeValueAsString(contextMap(request)));
        root.put("store", false);
        root.put("max_output_tokens", 300);
        root.put("text", Collections.singletonMap("format", responseFormat(request)));
        return mapper.writeValueAsString(root);
    }

    private Map<String, Object> contextMap(TripAssistantRequest request) {
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("destination", request.getDestination());
        context.put("trip_date", String.valueOf(request.getDate()));
        context.put("trip_start", String.valueOf(request.getStartTime()));
        context.put("trip_end", String.valueOf(request.getEndTime()));
        context.put("transportation_mode", request.getTransportationMode().name());

        List<Map<String, Object>> available = new ArrayList<Map<String, Object>>();
        for (Activity activity : request.getActivities()) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("activity_id", activity.getId());
            value.put("name", activity.getName());
            value.put("category", activity.getCategory().name());
            value.put("rating", activity.getRating());
            value.put("duration_minutes", activity.getEstimatedDurationMinutes());
            value.put("opening_time", String.valueOf(activity.getOpeningTime()));
            value.put("closing_time", String.valueOf(activity.getClosingTime()));
            value.put("setting", activity.getIndoorOutdoorType().name());
            value.put("address", activity.getLocation().getAddress());
            available.add(value);
        }
        context.put("available_activities", available);
        context.put("bookmarked_activity_ids", request.getBookmarkedActivityIds());

        List<Map<String, Object>> plan = new ArrayList<Map<String, Object>>();
        for (ScheduledEvent event : request.getScheduledEvents()) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("start", String.valueOf(event.getStartTime()));
            value.put("end", String.valueOf(event.getEndTime()));
            value.put("event_type", event.getEventType().name());
            value.put("activity_id", event.getActivity() == null
                    ? null : event.getActivity().getId());
            plan.add(value);
        }
        context.put("day_plan", plan);

        List<Map<String, Object>> forecast = new ArrayList<Map<String, Object>>();
        for (WeatherWarning warning : request.getWeather()) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("time", String.valueOf(warning.getTime()));
            value.put("condition", warning.getWeatherCondition());
            value.put("severity", warning.getSeverity().name());
            value.put("message", warning.getMessage());
            forecast.add(value);
        }
        context.put("weather", forecast);
        context.put("history", historyMap(request.getHistory()));
        context.put("question", request.getQuestion());
        return context;
    }

    private List<Map<String, Object>> historyMap(List<TripAssistantMessage> history) {
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        int start = Math.max(0, history.size() - 8);
        for (int index = start; index < history.size(); index++) {
            TripAssistantMessage message = history.get(index);
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("role", message.getRole().name().toLowerCase(Locale.ROOT));
            value.put("text", message.getText());
            value.put("grounded_activity_ids", message.getActivityIds());
            values.add(value);
        }
        return values;
    }

    private Map<String, Object> responseFormat(TripAssistantRequest request) {
        List<String> ids = new ArrayList<String>();
        for (Activity activity : request.getActivities()) {
            ids.add(activity.getId());
        }
        Map<String, Object> idItems = new LinkedHashMap<String, Object>();
        idItems.put("type", "string");
        idItems.put("enum", ids);
        Map<String, Object> idArray = new LinkedHashMap<String, Object>();
        idArray.put("type", "array");
        idArray.put("items", idItems);
        idArray.put("maxItems", 3);

        Map<String, Object> intent = new LinkedHashMap<String, Object>();
        intent.put("type", "string");
        intent.put("enum", Arrays.asList(
                "RECOMMEND", "RAIN", "AFTERNOON", "BOOKMARKS", "EXPLAIN", "GENERAL"));
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("intent", intent);
        properties.put("activity_ids", idArray);
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Arrays.asList("intent", "activity_ids"));
        schema.put("additionalProperties", false);

        Map<String, Object> format = new LinkedHashMap<String, Object>();
        format.put("type", "json_schema");
        format.put("name", "trip_activity_selection");
        format.put("schema", schema);
        format.put("strict", true);
        return format;
    }

    private TripAssistantDecision parseResponse(String body) throws JsonProcessingException {
        JsonNode response = mapper.readTree(body);
        if (!"completed".equals(response.path("status").asText())) {
            throw new IllegalStateException("OpenAI returned an incomplete response");
        }
        String outputText = "";
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) {
                continue;
            }
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) {
                    throw new IllegalStateException("OpenAI declined the request");
                }
                if ("output_text".equals(content.path("type").asText())) {
                    outputText = content.path("text").asText();
                }
            }
        }
        if (outputText.isEmpty()) {
            throw new IllegalStateException("OpenAI returned no assistant text");
        }
        JsonNode selection = mapper.readTree(outputText);
        TripAssistantDecision.Intent intent = TripAssistantDecision.Intent.valueOf(
                selection.path("intent").asText("GENERAL"));
        List<String> ids = new ArrayList<String>();
        for (JsonNode id : selection.path("activity_ids")) {
            ids.add(id.asText());
        }
        return new TripAssistantDecision(intent, ids);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
