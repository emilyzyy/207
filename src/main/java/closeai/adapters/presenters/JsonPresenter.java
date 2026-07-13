package closeai.adapters.presenters;

import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import java.util.List;

public final class JsonPresenter {
    public String activities(List<Activity> activities) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < activities.size(); i++) {
            if (i > 0) json.append(',');
            json.append(activity(activities.get(i)));
        }
        return json.append(']').toString();
    }

    public String activity(Activity a) {
        return "{\"id\":\"" + escape(a.getId()) + "\",\"name\":\"" + escape(a.getName())
                + "\",\"category\":\"" + a.getCategory() + "\",\"rating\":" + a.getRating()
                + ",\"address\":\"" + escape(a.getLocation().getAddress()) + "\",\"latitude\":"
                + a.getLocation().getLatitude() + ",\"longitude\":" + a.getLocation().getLongitude()
                + ",\"durationMinutes\":" + a.getEstimatedDurationMinutes() + ",\"type\":\""
                + a.getIndoorOutdoorType() + "\",\"weatherRisk\":\"" + escape(a.getWeatherRisk()) + "\"}";
    }

    public String trip(Trip trip) {
        StringBuilder bookmarks = new StringBuilder("[");
        for (int i = 0; i < trip.getBookmarkedActivities().size(); i++) {
            if (i > 0) bookmarks.append(',');
            bookmarks.append(activity(trip.getBookmarkedActivities().get(i)));
        }
        bookmarks.append(']');
        StringBuilder events = new StringBuilder("[");
        for (int i = 0; i < trip.getScheduledEvents().size(); i++) {
            if (i > 0) events.append(',');
            events.append(event(trip.getScheduledEvents().get(i)));
        }
        events.append(']');
        return "{\"id\":\"" + trip.getId() + "\",\"destination\":\"" + escape(trip.getDestination())
                + "\",\"date\":\"" + trip.getDate() + "\",\"startTime\":\"" + trip.getStartTime()
                + "\",\"endTime\":\"" + trip.getEndTime() + "\",\"transportationMode\":\""
                + trip.getTransportationMode() + "\",\"bookmarks\":" + bookmarks + ",\"events\":" + events + "}";
    }

    public String event(ScheduledEvent event) {
        return "{\"id\":\"" + event.getId() + "\",\"eventType\":\"" + event.getEventType()
                + "\",\"startTime\":\"" + event.getStartTime() + "\",\"endTime\":\"" + event.getEndTime()
                + "\",\"notes\":\"" + escape(event.getNotes()) + "\",\"activity\":"
                + (event.getActivity() == null ? "null" : activity(event.getActivity())) + "}";
    }

    public String weather(WeatherWarning warning) {
        return "{\"condition\":\"" + escape(warning.getWeatherCondition()) + "\",\"severity\":\""
                + warning.getSeverity() + "\",\"message\":\"" + escape(warning.getMessage()) + "\"}";
    }

    public String message(String value) { return "{\"message\":\"" + escape(value) + "\"}"; }
    public String error(String value) { return "{\"error\":\"" + escape(value) + "\"}"; }
    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
