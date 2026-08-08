package trippy.application.tripassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Values supplied by the Swing controller for one George chat turn. */
public final class TripAssistantInputData {
    private final String tripId;
    private final String question;
    private final List<TripAssistantMessage> history;

    public TripAssistantInputData(
            String tripId, String question, List<TripAssistantMessage> history) {
        this.tripId = tripId == null ? "" : tripId.trim();
        this.question = question == null ? "" : question.trim();
        this.history = Collections.unmodifiableList(new ArrayList<TripAssistantMessage>(
                history == null ? Collections.<TripAssistantMessage>emptyList() : history));
    }

    public String getTripId() { return tripId; }

    public String getQuestion() { return question; }

    public List<TripAssistantMessage> getHistory() { return history; }
}
