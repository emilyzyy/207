package closeai.application.tripassistant;

import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.IndoorOutdoorType;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Builds conversational text exclusively from validated application entities. */
public final class TripAssistantResponseFormatter {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final Pattern CONTEXTUAL_REFERENCE = Pattern.compile(
            "\\b(it|its|they|them|their|this|that|these|those)\\b");

    public TripAssistantOutputData format(
            TripAssistantRequest request, TripAssistantDecision decision) {
        Map<String, Activity> allowed = new LinkedHashMap<String, Activity>();
        for (Activity activity : request.getActivities()) {
            allowed.put(activity.getId(), activity);
        }
        List<Activity> selected = new ArrayList<Activity>();
        List<String> groundedIds = new ArrayList<String>();
        for (String id : decision.getActivityIds()) {
            Activity activity = allowed.get(id);
            if (activity != null && !groundedIds.contains(id) && groundedIds.size() < 3) {
                groundedIds.add(id);
                selected.add(activity);
            }
        }

        StringBuilder answer = new StringBuilder();
        if (!decision.getNotice().trim().isEmpty()) {
            answer.append(decision.getNotice().trim()).append("\n\n");
        }
        if (decision.getIntent() == TripAssistantDecision.Intent.GENERAL
                && groundedIds.isEmpty() && !decision.getAnswer().trim().isEmpty()) {
            answer.append(decision.getAnswer().trim());
            return new TripAssistantOutputData(answer.toString().trim(), groundedIds);
        }
        if (decision.getIntent() == TripAssistantDecision.Intent.ACTIVITY_DETAILS) {
            return formatActivityDetails(
                    answer, request, decision, allowed, selected, groundedIds);
        }
        if (decision.getIntent() == TripAssistantDecision.Intent.EXPLAIN) {
            return formatExplanation(answer, request, allowed, selected, groundedIds);
        }
        appendOpening(answer, request, decision, selected);
        appendActivities(answer, request, decision, selected);
        return new TripAssistantOutputData(answer.toString().trim(), groundedIds);
    }

    private TripAssistantOutputData formatActivityDetails(
            StringBuilder answer, TripAssistantRequest request,
            TripAssistantDecision decision, Map<String, Activity> allowed,
            List<Activity> selected, List<String> groundedIds) {
        FollowUpSelection selection = resolveFollowUp(
                request, allowed, selected, groundedIds);
        if (selection.needsClarification()) {
            answer.append(clarification(selection.getActivities()));
            return new TripAssistantOutputData(
                    answer.toString().trim(), selection.getActivityIds());
        }
        Activity activity = selection.getActivities().get(0);
        answer.append(activityFact(request, activity, decision.getRequestedFact()));
        return new TripAssistantOutputData(
                answer.toString().trim(), selection.getActivityIds());
    }

    private TripAssistantOutputData formatExplanation(
            StringBuilder answer, TripAssistantRequest request,
            Map<String, Activity> allowed, List<Activity> selected,
            List<String> groundedIds) {
        FollowUpSelection selection = resolveFollowUp(
                request, allowed, selected, groundedIds);
        if (selection.needsClarification()) {
            answer.append(clarification(selection.getActivities()));
            return new TripAssistantOutputData(
                    answer.toString().trim(), selection.getActivityIds());
        }
        for (int index = 0; index < selection.getActivities().size(); index++) {
            if (index > 0) {
                answer.append('\n');
            }
            answer.append(recommendationReason(
                    request, selection.getActivities().get(index)));
        }
        return new TripAssistantOutputData(
                answer.toString().trim(), selection.getActivityIds());
    }

    private FollowUpSelection resolveFollowUp(
            TripAssistantRequest request, Map<String, Activity> allowed,
            List<Activity> selected, List<String> groundedIds) {
        List<Activity> activities = new ArrayList<Activity>(selected);
        List<String> ids = new ArrayList<String>(groundedIds);
        List<Activity> recent = recentGroundedActivities(request, allowed);
        boolean contextual = CONTEXTUAL_REFERENCE.matcher(
                request.getQuestion().toLowerCase(Locale.ROOT)).find();
        if (contextual) {
            activities = recent;
            ids = activityIds(recent);
            return new FollowUpSelection(activities, ids, activities.size() != 1);
        }
        if (activities.isEmpty() && recent.size() == 1) {
            activities = recent;
            ids = activityIds(recent);
        }
        return new FollowUpSelection(activities, ids, activities.size() != 1);
    }

    private List<Activity> recentGroundedActivities(
            TripAssistantRequest request, Map<String, Activity> allowed) {
        for (int index = request.getHistory().size() - 1; index >= 0; index--) {
            TripAssistantMessage message = request.getHistory().get(index);
            if (message.getRole() != TripAssistantMessage.Role.ASSISTANT
                    || message.getActivityIds().isEmpty()) {
                continue;
            }
            List<Activity> result = new ArrayList<Activity>();
            for (String id : message.getActivityIds()) {
                Activity activity = allowed.get(id);
                if (activity != null && !result.contains(activity) && result.size() < 3) {
                    result.add(activity);
                }
            }
            return result;
        }
        return new ArrayList<Activity>();
    }

    private List<String> activityIds(List<Activity> activities) {
        List<String> result = new ArrayList<String>();
        for (Activity activity : activities) {
            result.add(activity.getId());
        }
        return result;
    }

    private String clarification(List<Activity> activities) {
        if (activities.isEmpty()) {
            return "Which activity do you mean? Mention one from this trip and I'll check "
                    + "the details CloseAI has for it.";
        }
        List<String> names = new ArrayList<String>();
        for (Activity activity : activities) {
            names.add(activity.getName());
        }
        return "Which activity do you mean—" + String.join(", ", names)
                + "? I don't want to guess.";
    }

    private String activityFact(
            TripAssistantRequest request, Activity activity,
            TripAssistantDecision.RequestedFact requestedFact) {
        switch (requestedFact) {
            case SPECIALTY:
                return "CloseAI records " + activity.getName() + " as "
                        + articleAndCategory(activity) + ", but it doesn't include its menu or "
                        + "signature specialty, so I don't want to make one up.";
            case CATEGORY:
                return "CloseAI records " + activity.getName() + " as "
                        + articleAndCategory(activity) + ".";
            case RATING:
                return String.format(Locale.ROOT, "%s has a %.1f rating in CloseAI.",
                        activity.getName(), activity.getRating());
            case HOURS:
                return activity.getName() + " is recorded as open from "
                        + TIME.format(activity.getOpeningTime()) + " to "
                        + TIME.format(activity.getClosingTime()) + ".";
            case DURATION:
                return "CloseAI estimates " + activity.getEstimatedDurationMinutes()
                        + " minutes for " + activity.getName() + ".";
            case LOCATION:
                String address = activity.getLocation().getAddress();
                if (address == null || address.trim().isEmpty()) {
                    return "CloseAI doesn't include an address for " + activity.getName()
                            + ", so I don't want to guess.";
                }
                return "CloseAI lists " + activity.getName() + " at " + address + ".";
            case SETTING:
                return activity.getName() + " is recorded as "
                        + setting(activity.getIndoorOutdoorType()) + ".";
            case BOOKMARK_STATUS:
                return activity.getName() + (request.getBookmarkedActivityIds()
                        .contains(activity.getId())
                        ? " is in your bookmarks." : " isn't in your bookmarks.");
            case PLAN_STATUS:
                return activity.getName() + (plannedIds(request.getScheduledEvents())
                        .contains(activity.getId())
                        ? " is already in your Day Plan." : " isn't in your Day Plan yet.");
            case RECOMMENDATION_REASON:
                return recommendationReason(request, activity);
            default:
                return "CloseAI doesn't include that detail for " + activity.getName()
                        + ", so I don't want to make it up. I can check its category, rating, "
                        + "hours, duration, address, setting, bookmark status, or Day Plan status.";
        }
    }

    private String recommendationReason(
            TripAssistantRequest request, Activity activity) {
        List<String> evidence = new ArrayList<String>();
        evidence.add(String.format(Locale.ROOT, "it has a %.1f rating", activity.getRating()));
        evidence.add("the visit is estimated at "
                + activity.getEstimatedDurationMinutes() + " minutes");
        evidence.add("its recorded hours are " + TIME.format(activity.getOpeningTime())
                + "–" + TIME.format(activity.getClosingTime()));
        if (request.getBookmarkedActivityIds().contains(activity.getId())) {
            evidence.add("you bookmarked it");
        }
        if (plannedIds(request.getScheduledEvents()).contains(activity.getId())) {
            evidence.add("it is already in your Day Plan");
        }
        return "I recommended " + activity.getName() + " because "
                + String.join(", ", evidence) + ".";
    }

    private String articleAndCategory(Activity activity) {
        String category;
        switch (activity.getCategory()) {
            case COFFEE:
                category = "café";
                break;
            case FOOD:
                category = "food activity";
                break;
            default:
                category = activity.getCategory().name().toLowerCase(Locale.ROOT);
                break;
        }
        String article = category.matches("^[aeiou].*") ? "an " : "a ";
        return article + category;
    }

    private String setting(IndoorOutdoorType type) {
        switch (type) {
            case INDOOR:
                return "indoors";
            case OUTDOOR:
                return "outdoors";
            default:
                return "a mix of indoor and outdoor space";
        }
    }

    private void appendOpening(StringBuilder answer, TripAssistantRequest request,
                               TripAssistantDecision decision, List<Activity> selected) {
        if (request.getActivities().isEmpty()) {
            answer.append("I don't have any activities in this trip's data yet. "
                    + "Load or search activities first, then ask me again.");
            return;
        }
        if (selected.isEmpty()) {
            answer.append("I couldn't find a suitable match among this trip's available "
                    + "activities. Try asking about bookmarks, rain, or a specific time of day.");
            return;
        }
        switch (decision.getIntent()) {
            case RAIN:
                answer.append(rainOpening(request));
                break;
            case AFTERNOON:
                answer.append("These options fit an afternoon visit within your "
                        + TIME.format(request.getStartTime()) + "–"
                        + TIME.format(request.getEndTime()) + " trip window:");
                break;
            case BOOKMARKS:
                answer.append("From your current bookmarks, I'd prioritize:");
                break;
            case EXPLAIN:
                answer.append("Here's why this choice works for your current trip:");
                break;
            case GENERAL:
                answer.append("Based on the trip information already in CloseAI:");
                break;
            default:
                answer.append("For your " + request.getDestination() + " trip on "
                        + request.getDate() + ", I'd recommend:");
                break;
        }
        answer.append('\n');
    }

    private String rainOpening(TripAssistantRequest request) {
        for (WeatherWarning warning : request.getWeather()) {
            String condition = warning.getWeatherCondition().toLowerCase(Locale.ROOT);
            if (condition.contains("rain") || condition.contains("shower")
                    || condition.contains("storm")) {
                return "The forecast includes " + warning.getWeatherCondition()
                        + ", so these existing activities are the strongest sheltered options:";
            }
        }
        return "If rain affects the trip, these existing activities are the strongest "
                + "sheltered options:";
    }

    private void appendActivities(StringBuilder answer, TripAssistantRequest request,
                                  TripAssistantDecision decision, List<Activity> selected) {
        if (selected.isEmpty()) {
            return;
        }
        Set<String> planned = plannedIds(request.getScheduledEvents());
        for (int index = 0; index < selected.size(); index++) {
            Activity activity = selected.get(index);
            answer.append(index + 1).append(". ").append(activity.getName()).append(" — ");
            List<String> reasons = new ArrayList<String>();
            if (request.getBookmarkedActivityIds().contains(activity.getId())) {
                reasons.add("already in your bookmarks");
            }
            if (planned.contains(activity.getId())) {
                reasons.add("already placed in your Day Plan");
            }
            if (decision.getIntent() == TripAssistantDecision.Intent.RAIN) {
                if (activity.getIndoorOutdoorType() == IndoorOutdoorType.INDOOR) {
                    reasons.add("recorded as indoor");
                } else if (activity.getIndoorOutdoorType() == IndoorOutdoorType.MIXED) {
                    reasons.add("recorded as mixed indoor/outdoor");
                }
            }
            reasons.add(String.format(Locale.ROOT, "rated %.1f", activity.getRating()));
            reasons.add(activity.getEstimatedDurationMinutes() + " minutes");
            reasons.add("open " + TIME.format(activity.getOpeningTime()) + "–"
                    + TIME.format(activity.getClosingTime()));
            answer.append(String.join(", ", reasons)).append(".\n");
        }
        answer.append("I used your current Day Plan, trip hours, weather, bookmarks, and "
                + request.getTransportationMode().name().toLowerCase(Locale.ROOT)
                + " travel mode. I only recommend activities currently stored in CloseAI.");
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

    private static final class FollowUpSelection {
        private final List<Activity> activities;
        private final List<String> activityIds;
        private final boolean clarification;

        private FollowUpSelection(
                List<Activity> activities, List<String> activityIds,
                boolean clarification) {
            this.activities = activities;
            this.activityIds = activityIds;
            this.clarification = clarification;
        }

        private List<Activity> getActivities() { return activities; }

        private List<String> getActivityIds() { return activityIds; }

        private boolean needsClarification() { return clarification; }
    }
}
