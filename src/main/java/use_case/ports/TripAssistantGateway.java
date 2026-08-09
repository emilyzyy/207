package use_case.ports;

import use_case.tripassistant.TripAssistantDecision;
import use_case.tripassistant.TripAssistantRequest;

/** Chooses grounded activities for a trip-assistant response. */
public interface TripAssistantGateway {
    TripAssistantDecision answer(TripAssistantRequest request);
}
