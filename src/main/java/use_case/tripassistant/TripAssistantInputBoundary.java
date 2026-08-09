package use_case.tripassistant;

/** Application boundary for one trip-assistant question. */
public interface TripAssistantInputBoundary {
    void execute(TripAssistantInputData inputData);
}
