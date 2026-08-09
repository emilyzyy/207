package entity.valueobjects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable chat-history item, including the grounded activities behind an answer. */
public final class TripAssistantMessage {
    public enum Role { USER, ASSISTANT }

    private final Role role;
    private final String text;
    private final List<String> activityIds;

    public TripAssistantMessage(Role role, String text) {
        this(role, text, Collections.<String>emptyList());
    }

    public TripAssistantMessage(Role role, String text, List<String> activityIds) {
        if (role == null || text == null) {
            throw new IllegalArgumentException("Chat message role and text are required");
        }
        this.role = role;
        this.text = text;
        this.activityIds = Collections.unmodifiableList(new ArrayList<String>(
                activityIds == null ? Collections.<String>emptyList() : activityIds));
    }

    public Role getRole() { return role; }

    public String getText() { return text; }

    public List<String> getActivityIds() { return activityIds; }
}
