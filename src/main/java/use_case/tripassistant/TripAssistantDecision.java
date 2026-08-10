package use_case.tripassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Structured gateway result with grounded activity IDs and an optional general-chat answer. */
public final class TripAssistantDecision {
    public enum Intent {
        RECOMMEND, RAIN, AFTERNOON, BOOKMARKS, EXPLAIN, ACTIVITY_DETAILS, GENERAL
    }

    /** The Trippy fact that Java should render for an activity follow-up. */
    public enum RequestedFact {
        SPECIALTY, CATEGORY, RATING, HOURS, DURATION, LOCATION, SETTING,
        BOOKMARK_STATUS, PLAN_STATUS, RECOMMENDATION_REASON, UNKNOWN
    }

    private final Intent intent;
    private final List<String> activityIds;
    private final String answer;
    private final String notice;
    private final RequestedFact requestedFact;

    public TripAssistantDecision(Intent intent, List<String> activityIds) {
        this(intent, activityIds, "", "", RequestedFact.UNKNOWN);
    }

    public TripAssistantDecision(Intent intent, List<String> activityIds, String notice) {
        this(intent, activityIds, "", notice, RequestedFact.UNKNOWN);
    }

    public TripAssistantDecision(
            Intent intent, List<String> activityIds, String answer, String notice) {
        this(intent, activityIds, answer, notice, RequestedFact.UNKNOWN);
    }

    public TripAssistantDecision(
            Intent intent, List<String> activityIds, String answer, String notice,
            RequestedFact requestedFact) {
        this.intent = intent == null ? Intent.GENERAL : intent;
        this.activityIds = Collections.unmodifiableList(new ArrayList<String>(
                activityIds == null ? Collections.<String>emptyList() : activityIds));
        this.answer = answer == null ? "" : answer;
        this.notice = notice == null ? "" : notice;
        this.requestedFact = requestedFact == null ? RequestedFact.UNKNOWN : requestedFact;
    }

    public Intent getIntent() {
        return intent;
    }

    public List<String> getActivityIds() {
        return activityIds;
    }

    public String getAnswer() {
        return answer;
    }

    public String getNotice() {
        return notice;
    }

    public RequestedFact getRequestedFact() {
        return requestedFact;
    }
}
