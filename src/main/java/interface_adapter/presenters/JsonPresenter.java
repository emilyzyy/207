package interface_adapter.presenters;

import java.util.List;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.TripDay;
import entity.entities.WeatherWarning;

public final class JsonPresenter {
    /**
     * Performs the a ct iv it ie s operation.
     * @param activities the a ct iv it ie s value
     * @return the result of the operation
     */
    public String activities(List<Activity> activities) {
        final StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < activities.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(activity(activities.get(i)));
        }
        return json.append(']').toString();
    }

    /**
     * Performs the a ct iv it y operation.
     * @param a the a value
     * @return the result of the operation
     */
    public String activity(Activity a) {
        return "{\"id\":\"" + escape(a.getId()) + "\",\"name\":\"" + escape(a.getName())
                + "\",\"category\":\"" + a.getCategory() + "\",\"rating\":" + a.getRating()
                + ",\"address\":\"" + escape(a.getLocation().getAddress()) + "\",\"latitude\":"
                + a.getLocation().getLatitude() + ",\"longitude\":" + a.getLocation().getLongitude()
                + ",\"durationMinutes\":" + a.getEstimatedDurationMinutes() + ",\"type\":\""
                + a.getIndoorOutdoorType() + "\",\"weatherRisk\":\"" + escape(a.getWeatherRisk()) + "\"}";
    }

    /**
     * Performs the t ri p operation.
     * @param trip the t ri p value
     * @return the result of the operation
     */
    public String trip(Trip trip) {
        final StringBuilder bookmarks = new StringBuilder("[");
        for (int i = 0; i < trip.getBookmarkedActivities().size(); i++) {
            if (i > 0) {
                bookmarks.append(',');
            }
            bookmarks.append(activity(trip.getBookmarkedActivities().get(i)));
        }
        bookmarks.append(']');
        final StringBuilder events = new StringBuilder("[");
        for (int i = 0; i < trip.getScheduledEvents().size(); i++) {
            if (i > 0) {
                events.append(',');
            }
            events.append(event(trip.getScheduledEvents().get(i)));
        }
        events.append(']');
        final StringBuilder days = new StringBuilder("[");
        for (int i = 0; i < trip.getDayCount(); i++) {
            if (i > 0) {
                days.append(',');
            }
            days.append(day(trip.getDay(i)));
        }
        days.append(']');
        return "{\"id\":\"" + trip.getId() + "\",\"destination\":\"" + escape(trip.getDestination())
                + "\",\"date\":\"" + trip.getDate() + "\",\"startTime\":\"" + trip.getStartTime()
                + "\",\"endTime\":\"" + trip.getEndTime() + "\",\"transportationMode\":\""
                + trip.getTransportationMode() + "\",\"dayCount\":" + trip.getDayCount()
                + ",\"days\":" + days + ",\"bookmarks\":" + bookmarks + ",\"events\":" + events + "}";
    }

    private String day(TripDay day) {
        final StringBuilder events = new StringBuilder("[");
        for (int i = 0; i < day.getScheduledEvents().size(); i++) {
            if (i > 0) {
                events.append(',');
            }
            events.append(event(day.getScheduledEvents().get(i)));
        }
        events.append(']');
        return "{\"date\":\"" + day.getDate() + "\",\"startTime\":\"" + day.getStartTime()
                + "\",\"endTime\":\"" + day.getEndTime() + "\",\"events\":" + events + "}";
    }

    /**
     * Performs the e ve nt operation.
     * @param event the e ve nt value
     * @return the result of the operation
     */
    public String event(ScheduledEvent event) {
        return "{\"id\":\"" + event.getId() + "\",\"eventType\":\"" + event.getEventType()
                + "\",\"startTime\":\"" + event.getStartTime() + "\",\"endTime\":\"" + event.getEndTime()
                + "\",\"notes\":\"" + escape(event.getNotes()) + "\",\"activity\":"
                + (event.getActivity() == null ? "null" : activity(event.getActivity())) + "}";
    }

    /**
     * Performs the w ea th er operation.
     * @param warning the w ar ni ng value
     * @return the result of the operation
     */
    public String weather(WeatherWarning warning) {
        return "{\"time\":\"" + warning.getTime() + "\",\"condition\":\""
                + escape(warning.getWeatherCondition()) + "\",\"severity\":\""
                + warning.getSeverity() + "\",\"message\":\"" + escape(warning.getMessage()) + "\"}";
    }

    /**
     * Performs the h ou rl yw ea th er operation.
     * @param hourlyWeather the h ou rl yw ea th er value
     * @return the result of the operation
     */
    public String hourlyWeather(List<WeatherWarning> hourlyWeather) {
        final StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < hourlyWeather.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(weather(hourlyWeather.get(i)));
        }
        return json.append(']').toString();
    }

    /**
     * Performs the m es sa ge operation.
     * @param value the v al ue value
     * @return the result of the operation
     */
    public String message(String value) {
        return "{\"message\":\"" + escape(value) + "\"}";
    }

    /**
     * Performs the e rr or operation.
     * @param value the v al ue value
     * @return the result of the operation
     */
    public String error(String value) {
        return "{\"error\":\"" + escape(value) + "\"}";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
