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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic recommendation gateway used by default and whenever live AI is unavailable. */
public final class OfflineTripAssistantGateway implements TripAssistantGateway {
    private static final LocalTime AFTERNOON_START = LocalTime.NOON;
    private static final LocalTime AFTERNOON_END = LocalTime.of(17, 0);
    private static final Pattern SIMPLE_ARITHMETIC = Pattern.compile(
            ".*?(-?\\d+)\\s*([+\\-*/xX×])\\s*(-?\\d+).*", Pattern.DOTALL);
    private static final Pattern ACTIVITY_REFERENCE = Pattern.compile(
            "\\b(it|its|they|them|their|this place|that place)\\b");

    @Override
    public TripAssistantDecision answer(TripAssistantRequest request) {
        String question = request.getQuestion();
        TripAssistantDecision.RequestedFact requestedFact = requestedFactFor(question);
        List<String> explicitActivities = mentionedActivities(question, request.getActivities());
        TripAssistantDecision.Intent intent = intentFor(
                question, requestedFact, explicitActivities);
        if (intent == TripAssistantDecision.Intent.GENERAL) {
            return new TripAssistantDecision(
                    intent, Collections.<String>emptyList(),
                    friendlyGeneralAnswer(question), "", requestedFact);
        }
        if (intent == TripAssistantDecision.Intent.ACTIVITY_DETAILS) {
            List<String> referenced = explicitActivities.isEmpty()
                    ? previousRecommendations(request.getHistory()) : explicitActivities;
            return new TripAssistantDecision(
                    intent, referenced, "", "", requestedFact);
        }
        if (intent == TripAssistantDecision.Intent.EXPLAIN) {
            List<String> previous = previousRecommendations(request.getHistory());
            if (!previous.isEmpty()) {
                return new TripAssistantDecision(
                        intent, previous, "", "",
                        TripAssistantDecision.RequestedFact.RECOMMENDATION_REASON);
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

    private TripAssistantDecision.Intent intentFor(
            String question, TripAssistantDecision.RequestedFact requestedFact,
            List<String> mentionedActivities) {
        String normalized = question.toLowerCase(Locale.ROOT);
        if (normalized.contains("why")) {
            return TripAssistantDecision.Intent.EXPLAIN;
        }
        if (requestedFact != TripAssistantDecision.RequestedFact.UNKNOWN
                || ((!mentionedActivities.isEmpty() || hasActivityReference(normalized))
                && containsUnsupportedActivityFact(normalized))) {
            return TripAssistantDecision.Intent.ACTIVITY_DETAILS;
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

    private TripAssistantDecision.RequestedFact requestedFactFor(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        if (normalized.contains("specialty") || normalized.contains("speciality")
                || normalized.contains("signature") || normalized.contains("menu")
                || normalized.contains("drink")) {
            return TripAssistantDecision.RequestedFact.SPECIALTY;
        }
        if (normalized.contains("rating") || normalized.contains("rated")
                || normalized.contains("review score")) {
            return TripAssistantDecision.RequestedFact.RATING;
        }
        if (normalized.contains("open") || normalized.contains("close")
                || normalized.contains("hours")) {
            return TripAssistantDecision.RequestedFact.HOURS;
        }
        if (normalized.contains("duration") || normalized.contains("how long")) {
            return TripAssistantDecision.RequestedFact.DURATION;
        }
        if (normalized.contains("where") || normalized.contains("address")
                || normalized.contains("location")) {
            return TripAssistantDecision.RequestedFact.LOCATION;
        }
        if (normalized.contains("indoors") || normalized.contains("indoor")
                || normalized.contains("outdoors") || normalized.contains("outdoor")
                || normalized.contains("setting")) {
            return TripAssistantDecision.RequestedFact.SETTING;
        }
        if ((normalized.contains("bookmark") || normalized.contains("saved"))
                && (hasActivityReference(normalized) || normalized.contains("status")
                || normalized.startsWith("did i") || normalized.startsWith("is "))) {
            return TripAssistantDecision.RequestedFact.BOOKMARK_STATUS;
        }
        if ((normalized.contains("day plan") || normalized.contains("scheduled"))
                && (hasActivityReference(normalized) || normalized.contains("status")
                || normalized.startsWith("is "))) {
            return TripAssistantDecision.RequestedFact.PLAN_STATUS;
        }
        if (normalized.contains("category") || normalized.contains("what kind")
                || normalized.contains("what type")) {
            return TripAssistantDecision.RequestedFact.CATEGORY;
        }
        return TripAssistantDecision.RequestedFact.UNKNOWN;
    }

    private List<String> mentionedActivities(
            String question, List<Activity> activities) {
        String normalized = question.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (Activity activity : activities) {
            String name = activity.getName().toLowerCase(Locale.ROOT);
            String id = activity.getId().toLowerCase(Locale.ROOT);
            if (normalized.contains(name)
                    || (id.length() >= 3 && normalized.contains(id))) {
                result.add(activity.getId());
            }
        }
        return result;
    }

    private boolean containsUnsupportedActivityFact(String question) {
        return question.contains("price") || question.contains("cost")
                || question.contains("history") || question.contains("historical")
                || question.contains("founded") || question.contains("tell me about");
    }

    private boolean hasActivityReference(String question) {
        return ACTIVITY_REFERENCE.matcher(question).find();
    }

    private String friendlyGeneralAnswer(String question) {
        String normalized = question.toLowerCase(Locale.ROOT).trim();
        Matcher arithmetic = SIMPLE_ARITHMETIC.matcher(normalized);
        if (arithmetic.matches()) {
            long left = Long.parseLong(arithmetic.group(1));
            long right = Long.parseLong(arithmetic.group(3));
            String operator = arithmetic.group(2);
            if (("/".equals(operator)) && right == 0) {
                return "That division is undefined—you can't divide by zero.";
            }
            if ("+".equals(operator)) {
                return left + " + " + right + " = " + (left + right) + ".";
            }
            if ("-".equals(operator)) {
                return left + " - " + right + " = " + (left - right) + ".";
            }
            if ("*".equals(operator) || "x".equalsIgnoreCase(operator)
                    || "×".equals(operator)) {
                return left + " × " + right + " = " + (left * right) + ".";
            }
            double quotient = (double) left / (double) right;
            return String.format(Locale.ROOT, "%d ÷ %d = %s.",
                    left, right, Double.toString(quotient));
        }
        if (normalized.contains("your name") || normalized.contains("who are you")) {
            return "I'm George—your cheerful trip companion in CloseAI.";
        }
        if (normalized.contains("how are you")) {
            return "I'm doing great and happy to help—how are you?";
        }
        if (normalized.matches("^(hi|hello|hey|good morning|good afternoon|good evening)[!. ]*$")) {
            return "Hi! I'm George. What would you like to chat about?";
        }
        return "I'm George, your friendly CloseAI trip companion. I'm happy to chat or help "
                + "with the trip you're viewing.";
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
