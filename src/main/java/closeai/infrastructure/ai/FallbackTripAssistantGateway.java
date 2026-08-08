package closeai.infrastructure.ai;

import closeai.application.ports.TripAssistantGateway;
import closeai.application.tripassistant.TripAssistantDecision;
import closeai.application.tripassistant.TripAssistantMessage;
import closeai.application.tripassistant.TripAssistantRequest;
import closeai.domain.entities.Activity;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Uses deterministic recommendations when the live provider cannot complete a turn. */
public final class FallbackTripAssistantGateway implements TripAssistantGateway {
    private final TripAssistantGateway live;
    private final TripAssistantGateway offline;

    public FallbackTripAssistantGateway(
            TripAssistantGateway live, TripAssistantGateway offline) {
        this.live = live;
        this.offline = offline;
    }

    @Override
    public TripAssistantDecision answer(TripAssistantRequest request) {
        try {
            TripAssistantDecision decision = live.answer(request);
            if (isGrounded(decision, request)) {
                return decision;
            }
        } catch (RuntimeException ignored) {
            // The UI receives a deterministic answer and a clear fallback notice below.
        }
        TripAssistantDecision fallback = offline.answer(request);
        return new TripAssistantDecision(
                fallback.getIntent(), fallback.getActivityIds(),
                fallback.getAnswer(),
                "Live AI is unavailable, so George used offline mode.",
                fallback.getRequestedFact());
    }

    private boolean isGrounded(
            TripAssistantDecision decision, TripAssistantRequest request) {
        if (decision == null) {
            return false;
        }
        Set<String> allowed = new HashSet<String>();
        for (Activity activity : request.getActivities()) {
            allowed.add(activity.getId());
        }
        for (String id : decision.getActivityIds()) {
            if (!allowed.contains(id)) {
                return false;
            }
        }
        if (decision.getIntent() == TripAssistantDecision.Intent.GENERAL
                && decision.getActivityIds().isEmpty()) {
            return !decision.getAnswer().trim().isEmpty()
                    && !requiresGroundedTripAnswer(request);
        }
        return true;
    }

    private boolean requiresGroundedTripAnswer(TripAssistantRequest request) {
        String question = request.getQuestion().toLowerCase(Locale.ROOT);
        if (containsAny(question,
                "recommend", "suggest", "activity", "place to", "visit",
                "what should i do", "rain", "weather", "afternoon",
                "bookmark", "saved", "day plan", "scheduled")) {
            return true;
        }
        boolean referencesActivity = hasRecentGroundedActivity(request);
        for (Activity activity : request.getActivities()) {
            if (question.contains(activity.getName().toLowerCase(Locale.ROOT))) {
                referencesActivity = true;
            }
        }
        return referencesActivity && containsAny(question,
                " it", "its ", "they", "them", "their", "that place", "this place",
                "specialty", "speciality", "signature", "menu", "drink", "category",
                "rating", "rated", "open", "close", "hours", "duration", "how long",
                "where", "address", "location", "indoor", "outdoor", "setting",
                "price", "cost", "history", "historical", "founded", "why");
    }

    private boolean hasRecentGroundedActivity(TripAssistantRequest request) {
        for (int index = request.getHistory().size() - 1; index >= 0; index--) {
            TripAssistantMessage message = request.getHistory().get(index);
            if (message.getRole() == TripAssistantMessage.Role.ASSISTANT
                    && !message.getActivityIds().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
