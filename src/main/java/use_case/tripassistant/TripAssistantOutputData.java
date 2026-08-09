package use_case.tripassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Grounded, display-ready answer returned by the use case. */
public final class TripAssistantOutputData {
    private final String answer;
    private final List<String> activityIds;

    public TripAssistantOutputData(String answer, List<String> activityIds) {
        this.answer = answer;
        this.activityIds = Collections.unmodifiableList(new ArrayList<String>(activityIds));
    }

    public String getAnswer() { return answer; }

    public List<String> getActivityIds() { return activityIds; }
}
