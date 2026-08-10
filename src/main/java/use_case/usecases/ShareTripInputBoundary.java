package use_case.usecases;

/** Application boundary for producing a portable, human-readable itinerary. */
public interface ShareTripInputBoundary {
    void execute(String tripId);
}
