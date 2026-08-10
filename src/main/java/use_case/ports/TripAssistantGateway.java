package use_case.ports;

import use_case.tripassistant.TripAssistantDecision;
import use_case.tripassistant.TripAssistantRequest;

/** Chooses grounded activities for a trip-assistant response. */
public interface TripAssistantGateway {
    /**
     * Performs the a ns we r operation.
     * @param request the r eq ue st value
     * @return the result of the operation
     */
    TripAssistantDecision answer(TripAssistantRequest request);
}
