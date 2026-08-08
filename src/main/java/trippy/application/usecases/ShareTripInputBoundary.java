package trippy.application.usecases;

/** Application boundary for producing a portable, human-readable itinerary. */
public interface ShareTripInputBoundary {
    String execute(String tripId);
}
