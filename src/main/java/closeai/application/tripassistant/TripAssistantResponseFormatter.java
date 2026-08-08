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

/** Builds conversational text exclusively from validated application entities. */
public final class TripAssistantResponseFormatter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

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
        appendOpening(answer, request, decision, selected);
        appendActivities(answer, request, decision, selected);
        return new TripAssistantOutputData(answer.toString().trim(), groundedIds);
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
}
