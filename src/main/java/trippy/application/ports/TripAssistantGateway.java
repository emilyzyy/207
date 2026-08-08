package trippy.application.ports;

import trippy.application.tripassistant.TripAssistantDecision;
import trippy.application.tripassistant.TripAssistantRequest;

/** Chooses grounded activities for a trip-assistant response. */
public interface TripAssistantGateway {
    TripAssistantDecision answer(TripAssistantRequest request);
}
