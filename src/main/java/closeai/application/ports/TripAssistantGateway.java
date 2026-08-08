package closeai.application.ports;

import closeai.application.tripassistant.TripAssistantDecision;
import closeai.application.tripassistant.TripAssistantRequest;

/** Chooses grounded activities for a trip-assistant response. */
public interface TripAssistantGateway {
    TripAssistantDecision answer(TripAssistantRequest request);
}
