package closeai.infrastructure.ai;

import closeai.application.ports.TripAssistantGateway;
import closeai.application.tripassistant.TripAssistantDecision;
import closeai.application.tripassistant.TripAssistantMessage;
import closeai.application.tripassistant.TripAssistantRequest;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.valueobjects.IndoorOutdoorType;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic recommendation gateway used by default and whenever live AI is unavailable. */
public final class OfflineTripAssistantGateway implements TripAssistantGateway {
    private static final LocalTime AFTERNOON_START = LocalTime.NOON;
    private static final LocalTime AFTERNOON_END = LocalTime.of(17, 0);

    @Override
    public TripAssistantDecision answer(TripAssistantRequest request) {
        TripAssistantDecision.Intent intent = intentFor(request.getQuestion());
        if (intent == TripAssistantDecision.Intent.EXPLAIN) {
            List<String> previous = previousRecommendations(request.getHistory());
            if (!previous.isEmpty()) {
                return new TripAssistantDecision(intent, previous);
            }
        }
        Set<String> planned = plannedIds(request.getScheduledEvents());
        List<ScoredActivity> scored = new ArrayList<ScoredActivity>();
        for (Activity activity : request.getActivities()) {
            boolean alreadyPlanned = planned.contains(activity.getId());
            if (!alreadyPlanned && !fitsFreeWindow(activity, request, false)) {
                continue;
            }
            if (intent == TripAssistantDecision.Intent.BOOKMARKS
                    && !request.getBookmarkedActivityIds().contains(activity.getId())) {
                continue;
            }
            if (intent == TripAssistantDecision.Intent.RAIN
                    && activity.getIndoorOutdoorType() == IndoorOutdoorType.OUTDOOR) {
                continue;
            }
            if (intent == TripAssistantDecision.Intent.AFTERNOON
                    && !(alreadyPlanned && isPlannedInAfternoon(activity, request))
                    && !fitsFreeWindow(activity, request, true)) {
                continue;
            }
            double score = activity.getRating() * 100.0;
            if (request.getBookmarkedActivityIds().contains(activity.getId())) {
                score += 140.0;
            }
            if (!planned.contains(activity.getId())) {
                score += 35.0;
            }
            if (intent == TripAssistantDecision.Intent.RAIN) {
                score += activity.getIndoorOutdoorType() == IndoorOutdoorType.INDOOR
                        ? 300.0 : 120.0;
            }
            scored.add(new ScoredActivity(activity, score));
        }
        Collections.sort(scored, Comparator
                .comparingDouble(ScoredActivity::getScore).reversed()
                .thenComparing(item -> item.getActivity().getId()));
        List<String> ids = new ArrayList<String>();
        for (ScoredActivity item : scored) {
            ids.add(item.getActivity().getId());
            if (ids.size() == 3) {
                break;
            }
        }
        return new TripAssistantDecision(intent, ids);
    }

    private TripAssistantDecision.Intent intentFor(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        if (normalized.contains("why")) {
            return TripAssistantDecision.Intent.EXPLAIN;
        }
        if (normalized.contains("rain") || normalized.contains("weather")) {
            return TripAssistantDecision.Intent.RAIN;
        }
        if (normalized.contains("afternoon")) {
            return TripAssistantDecision.Intent.AFTERNOON;
        }
        if (normalized.contains("bookmark") || normalized.contains("saved")) {
            return TripAssistantDecision.Intent.BOOKMARKS;
        }
        if (normalized.contains("recommend") || normalized.contains("activity")
                || normalized.contains("visit") || normalized.contains("do")) {
            return TripAssistantDecision.Intent.RECOMMEND;
        }
        return TripAssistantDecision.Intent.GENERAL;
    }

    private boolean fitsFreeWindow(
            Activity activity, TripAssistantRequest request, boolean afternoonOnly) {
        LocalTime start = later(activity.getOpeningTime(), request.getStartTime());
        LocalTime end = earlier(activity.getClosingTime(), request.getEndTime());
        if (afternoonOnly) {
            start = later(start, AFTERNOON_START);
            end = earlier(end, AFTERNOON_END);
        }
        LocalTime cursor = start;
        List<ScheduledEvent> ordered = new ArrayList<ScheduledEvent>(request.getScheduledEvents());
        Collections.sort(ordered, Comparator.comparing(ScheduledEvent::getStartTime));
        for (ScheduledEvent event : ordered) {
            if (!event.getEndTime().isAfter(cursor) || !event.getStartTime().isBefore(end)) {
                continue;
            }
            if (event.getStartTime().isAfter(cursor)
                    && minutes(cursor, event.getStartTime())
                    >= activity.getEstimatedDurationMinutes()) {
                return true;
            }
            if (event.getEndTime().isAfter(cursor)) {
                cursor = event.getEndTime();
            }
            if (!cursor.isBefore(end)) {
                return false;
            }
        }
        return minutes(cursor, end) >= activity.getEstimatedDurationMinutes();
    }

    private boolean isPlannedInAfternoon(
            Activity activity, TripAssistantRequest request) {
        for (ScheduledEvent event : request.getScheduledEvents()) {
            if (event.getActivity() != null
                    && event.getActivity().getId().equals(activity.getId())
                    && event.getStartTime().isBefore(AFTERNOON_END)
                    && event.getEndTime().isAfter(AFTERNOON_START)) {
                return true;
            }
        }
        return false;
    }

    private long minutes(LocalTime start, LocalTime end) {
        return Duration.between(start, end).toMinutes();
    }

    private LocalTime later(LocalTime first, LocalTime second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalTime earlier(LocalTime first, LocalTime second) {
        return first.isBefore(second) ? first : second;
    }

    private Set<String> plannedIds(List<ScheduledEvent> events) {
        Set<String> result = new HashSet<String>();
        for (ScheduledEvent event : events) {
            if (event.getActivity() != null) {
                result.add(event.getActivity().getId());
            }
        }
        return result;
    }

    private List<String> previousRecommendations(List<TripAssistantMessage> history) {
        for (int index = history.size() - 1; index >= 0; index--) {
            TripAssistantMessage message = history.get(index);
            if (message.getRole() == TripAssistantMessage.Role.ASSISTANT
                    && !message.getActivityIds().isEmpty()) {
                return message.getActivityIds();
            }
        }
        return Collections.emptyList();
    }

    private static final class ScoredActivity {
        private final Activity activity;
        private final double score;

        private ScoredActivity(Activity activity, double score) {
            this.activity = activity;
            this.score = score;
        }

        private Activity getActivity() { return activity; }

        private double getScore() { return score; }
    }
}
