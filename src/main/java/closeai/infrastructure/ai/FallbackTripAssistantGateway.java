package closeai.infrastructure.ai;

import closeai.application.ports.TripAssistantGateway;
import closeai.application.tripassistant.TripAssistantDecision;
import closeai.application.tripassistant.TripAssistantRequest;
import closeai.domain.entities.Activity;
import java.util.HashSet;
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
                "Live AI is unavailable, so George used offline recommendations.");
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
            return !decision.getAnswer().trim().isEmpty();
        }
        return true;
    }
}
