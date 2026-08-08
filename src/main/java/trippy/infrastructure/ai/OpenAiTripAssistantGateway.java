package trippy.infrastructure.ai;

import trippy.application.ports.TripAssistantGateway;
import trippy.application.tripassistant.TripAssistantDecision;
import trippy.application.tripassistant.TripAssistantMessage;
import trippy.application.tripassistant.TripAssistantRequest;
import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.WeatherWarning;
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

/** Live OpenAI Responses API implementation with schema-constrained chat output. */
public final class OpenAiTripAssistantGateway implements TripAssistantGateway {
    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.openai.com/v1/responses");
    private static final String INSTRUCTIONS = "Role: You are George, the friendly travel "
            + "companion inside Trippy. Be cheerful, natural, and concise. Answer ordinary "
            + "questions naturally and directly. "
            + "For greetings, identity questions, simple math, or other general questions, use "
            + "intent GENERAL, return no activity IDs, and put the direct reply in answer. "
            + "For trip advice, choose up to three suitable activities using every supplied trip "
            + "field as evidence. Select only activity_id values from available_activities; never "
            + "create a place, name, or ID. Do not name places in answer because Trippy renders "
            + "validated activity names locally. Use bookmarks, Day Plan, weather, hours, "
            + "duration, date, and transportation mode. For a follow-up asking about an activity, "
            + "use ACTIVITY_DETAILS and identify the requested_fact. Reuse the single most recent "
            + "grounded activity ID for pronouns such as it, its, their, or that place. If recent "
            + "history grounds more than one possible activity, return no ID so Trippy can ask "
            + "for clarification. Use EXPLAIN for why a recommendation was made. Never put place "
            + "facts, menu details, specialties, prices, or history in answer; Java renders all "
            + "activity facts from Trippy entities and will honestly report missing data. Return "
            + "only the requested structured data.";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final boolean sendAuthorization;

    public OpenAiTripAssistantGateway(String apiKey, String model) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(), DEFAULT_ENDPOINT, apiKey, model, Duration.ofSeconds(30), true);
    }

    public OpenAiTripAssistantGateway(
            HttpClient client, ObjectMapper mapper, URI endpoint, String apiKey,
            String model, Duration timeout) {
        this(client, mapper, endpoint, apiKey, model, timeout, true);
    }

    /** Builds a client for a trusted proxy that keeps the OpenAI key on the server. */
    public static OpenAiTripAssistantGateway viaProxy(URI endpoint, String model) {
        return new OpenAiTripAssistantGateway(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(), endpoint, null, model, Duration.ofSeconds(30), false);
    }

    private OpenAiTripAssistantGateway(
            HttpClient client, ObjectMapper mapper, URI endpoint, String apiKey,
            String model, Duration timeout, boolean sendAuthorization) {
        if (client == null || mapper == null || endpoint == null
                || (isBlank(apiKey) && sendAuthorization) || isBlank(model) || timeout == null) {
            throw new IllegalArgumentException("OpenAI client configuration is required");
        }
        this.client = client;
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.sendAuthorization = sendAuthorization;
    }

    @Override
    public TripAssistantDecision answer(TripAssistantRequest request) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson(request)));
            if (sendAuthorization) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest httpRequest = requestBuilder.build();
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
        if (!ids.isEmpty()) {
            idItems.put("enum", ids);
        }
        Map<String, Object> idArray = new LinkedHashMap<String, Object>();
        idArray.put("type", "array");
        idArray.put("items", idItems);
        idArray.put("maxItems", ids.isEmpty() ? 0 : 3);

        Map<String, Object> intent = new LinkedHashMap<String, Object>();
        intent.put("type", "string");
        intent.put("enum", Arrays.asList(
                "RECOMMEND", "RAIN", "AFTERNOON", "BOOKMARKS", "EXPLAIN",
                "ACTIVITY_DETAILS", "GENERAL"));
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("intent", intent);
        properties.put("activity_ids", idArray);
        Map<String, Object> answer = new LinkedHashMap<String, Object>();
        answer.put("type", "string");
        answer.put("minLength", 1);
        answer.put("maxLength", 1200);
        properties.put("answer", answer);
        Map<String, Object> requestedFact = new LinkedHashMap<String, Object>();
        requestedFact.put("type", "string");
        requestedFact.put("enum", Arrays.asList(
                "SPECIALTY", "CATEGORY", "RATING", "HOURS", "DURATION", "LOCATION",
                "SETTING", "BOOKMARK_STATUS", "PLAN_STATUS", "RECOMMENDATION_REASON",
                "UNKNOWN"));
        properties.put("requested_fact", requestedFact);
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Arrays.asList(
                "intent", "activity_ids", "answer", "requested_fact"));
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
        String answer = selection.path("answer").asText("").trim();
        TripAssistantDecision.RequestedFact requestedFact =
                TripAssistantDecision.RequestedFact.valueOf(
                        selection.path("requested_fact").asText("UNKNOWN"));
        return new TripAssistantDecision(intent, ids, answer, "", requestedFact);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
