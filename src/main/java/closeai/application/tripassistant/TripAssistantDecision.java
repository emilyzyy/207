package closeai.application.tripassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Gateway result limited to intent and activity IDs so infrastructure cannot invent places. */
public final class TripAssistantDecision {
    public enum Intent { RECOMMEND, RAIN, AFTERNOON, BOOKMARKS, EXPLAIN, GENERAL }

    private final Intent intent;
    private final List<String> activityIds;
    private final String notice;

    public TripAssistantDecision(Intent intent, List<String> activityIds) {
        this(intent, activityIds, "");
    }

    public TripAssistantDecision(Intent intent, List<String> activityIds, String notice) {
        this.intent = intent == null ? Intent.GENERAL : intent;
        this.activityIds = Collections.unmodifiableList(new ArrayList<String>(
                activityIds == null ? Collections.<String>emptyList() : activityIds));
        this.notice = notice == null ? "" : notice;
    }

    public Intent getIntent() { return intent; }

    public List<String> getActivityIds() { return activityIds; }

    public String getNotice() { return notice; }
}
